package com.spotify.ladron.util

import java.time.Duration

import scala.util.Try

import enumeratum.{Enum, EnumEntry}
import org.tensorflow.framework.MetaGraphDef

import com.spotify.ladron.LadronJob
import com.spotify.ladron.assignment.{Assignment, AssignmentBucket}
import com.spotify.ladron.common.{AssignmentPolicy, PolicyType}
import com.spotify.ladron.config.MessagingModuleConfig
import com.spotify.ladron.syntax.tensorflow.ExampleRunner
import com.spotify.zoltar.Models
import com.spotify.zoltar.tf.TensorFlowModel

object LadronConfig {
  sealed trait Error extends Product with Serializable {
    def description: String
  }

  final case class ModelLoadFailed(private val message: String) extends Error {
    override def description: String = "LoadOfModelFailed: " + message
  }

  final case class ModelValidationFailed(private val message: String) extends Error {
    override def description: String = "ModelValidationFailure: " + message
  }

  final case class MissingPolicyOptions(private val assignments: Seq[AssignmentPolicy])
      extends Error {
    override def description: String =
      "MissingPolicyOptions for : " + assignments.map(_.name).mkString(", ")
  }

  sealed abstract class ErrorEnum(val description: String) extends EnumEntry with Error

  object Error extends Enum[ErrorEnum] {
    val values: scala.collection.immutable.IndexedSeq[ErrorEnum] = findValues

    case object ModelContextMismatch
        extends ErrorEnum("Either both or neither model uri and context must be present")
    case object MissingModel
        extends ErrorEnum(
          "Invalid configuration, no model provided but contextual assignment exists"
        )
    case class ModelAssignmentMismatches(
      assignedModelNames: List[String],
      suppliedModelNames: List[String]
    ) extends ErrorEnum(
          "mismatches between supplied model and assigned ones." +
            s" \n Assigned models: ${assignedModelNames.toString} " +
            s"\n Supplied models: ${suppliedModelNames.toString}"
        )
  }

  private def hasContextual(hasModelUri: Boolean, hasContext: Boolean): Either[Error, Boolean] =
    (hasModelUri, hasContext) match {
      case (true, true)   => Right(true)
      case (false, false) => Right(false)
      case (false, true)  => Right(false)
      case _ =>
        Left(Error.ModelContextMismatch)
    }

  def hasValidContextuals(
    modelUris: List[String],
    modelContext: Option[String]
  ): Either[Error, Boolean] = {
    def isValidModel(model: TensorFlowModel): Either[Error, Boolean] = {
      val graph = MetaGraphDef.parseFrom(model.instance().metaGraphDef())
      Try(ExampleRunner.parse(graph)).toEither.fold(
        t => Left(ModelValidationFailed(t.getMessage)),
        _ => Right(true)
      )
    }

    def withClose[T](thunk: TensorFlowModel => T)(model: TensorFlowModel): T = {
      val result = thunk(model)
      model.close()
      result
    }

    def hasValidModel(modelUri: String): Either[Error, Boolean] = {
      val modelLoadTimeoutMinutes: Long = 10
      Try(Models.tensorFlow(modelUri).get(Duration.ofMinutes(modelLoadTimeoutMinutes))).toEither
        .fold(t => Left(ModelLoadFailed(t.getMessage)), withClose(isValidModel))
    }

    hasContextual(modelUris.length > 0, modelContext.isDefined)
      .flatMap { contextual =>
        val badResponses = modelUris.map(hasValidModel(_)).filter(_.isLeft)

        val response = if (badResponses.nonEmpty) badResponses.head else Right(contextual)

        if (contextual) response else Right(contextual)
      }

  }

  private def assignablePolicies(
    assignments: AssignmentBucket[AssignmentPolicy]
  ): Set[AssignmentPolicy] =
    assignments.cellWeights
      .flatMap {
        case (bucket, weight) if weight.value > 0 => bucket.cellWeights
        case _                                    => Seq.empty
      }
      .flatMap {
        case (assignment, weight) if weight.value > 0 => Some(assignment)
        case _                                        => None
      }
      .toSet

  private def hasModelAssignment(
    assignments: AssignmentBucket[AssignmentPolicy],
    args: LadronJob.Args
  ): Boolean = {
    val assignedModels = assignablePolicies(assignments)
      .flatMap(_.model)
      .map(_.value)

    assignedModels.nonEmpty
  }

  private def modelSupplied(
    assignments: AssignmentBucket[AssignmentPolicy],
    args: LadronJob.Args
  ): Either[Error, Boolean] = {
    val assignedModels = assignablePolicies(assignments)
      .flatMap(_.model)
      .map(_.value)

    if (assignedModels.subsetOf(args.contextModelNames.toSet)) {
      Right(true)
    } else {
      Left(Error.ModelAssignmentMismatches(assignedModels.toList, args.contextModelNames))
    }
  }

  private def checkModelAssignment(
    hasModelAssignment: Boolean,
    hasModel: Boolean
  ): Either[Error, Boolean] =
    if (hasModelAssignment && !hasModel) {
      Left(Error.MissingModel)
    } else {
      Right(hasModelAssignment)
    }

  def checkPolicyOptionsAssignment(
    config: MessagingModuleConfig,
    assignments: AssignmentBucket[AssignmentPolicy]
  ): Either[Error, _] = {
    val missingPolicies = assignablePolicies(assignments) -- config.optionByPolicy.keys.toSet
    if (missingPolicies.nonEmpty) {
      Left(MissingPolicyOptions(missingPolicies.toSeq))
    } else {
      Right(null)
    }
  }

  def check(args: LadronJob.Args): Either[Error, LadronJob.Args] = {
    val errorOrHasModel = hasValidContextuals(
      modelUris = args.contextModelUris,
      modelContext = args.candidateContext
    )

    val assignments =
      Assignment.assignmentOn(
        args.partition.toDate,
        args.config.assignment,
        args.config.buckets,
        args.config.moduleBucketSalt
      )

    val modelAssignment = hasModelAssignment(assignments, args)
    errorOrHasModel
      .flatMap(hasModel => checkModelAssignment(modelAssignment, hasModel))
      .flatMap(_ => modelSupplied(assignments, args))
      .flatMap(_ => checkPolicyOptionsAssignment(args.config, assignments))
      .map(_ => args)
  }
}
