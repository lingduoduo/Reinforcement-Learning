package com.spotify.ladron

import java.time.{LocalDate, LocalDateTime}

import com.google.protobuf.ByteString
import com.spotify.ladron.Coders._
import com.spotify.ladron.assignment.{AssignmentJob, UserAssignment}
import com.spotify.ladron.candidate.{
  CandidateMessage,
  DropImmediately,
  SendAtDateTime,
  SendAtTime,
  UserSelectionInfo
}
import com.spotify.ladron.common.{MessageChannel, MessagingModule}
import com.spotify.ladron.config.Campaigns.LadronCampaign
import com.spotify.ladron.config.{Campaigns, MessagingModuleConfig}
import com.spotify.ladron.delivery.{Message, MessageBuilder}
import com.spotify.ladron.eligibility.{MatchedCampaign, MatchedUser, _}
import com.spotify.ladron.model._
import com.spotify.ladron.selection.{
  CandidateContext,
  MessageSelectionJob,
  PartitionString,
  SelectionId
}
import com.spotify.ladron.syntax.agent.BanditContextSyntax
import com.spotify.ladron.syntax.metrics.MetricsSyntax
import com.spotify.ladron.train.ContextFreeModelByContext
import com.spotify.ladron.util._
import com.spotify.message.data.MessageHistory
import com.spotify.scio
import com.spotify.scio.ContextAndArgs
import com.spotify.multiple.syntax._
import com.spotify.scio.avro._
import com.spotify.scio.extra.json._
import com.spotify.scio.tensorflow._
import com.spotify.scio.values.SCollection
import org.apache.beam.sdk.metrics.Counter
import org.tensorflow.example.{BytesList, Example, Feature}

/**
 * Job that runs through all the ladron pipelines for candidate messages.
 *
 * ReadCandidates
 *  | AssignmentJob
 *  | MessageHistoryFilter
 *  | MessageTemplateSelection
 */
object LadronJob extends ScioJobBase {
  override val metricRegistry: MetricRegistry =
    MetricRegistry.createMetricRegistry("ladron_metrics")

  metricRegistry.register(MessageSelectionJob.metricRegistry)
  metricRegistry.register(AssignmentJob.metricRegistry)
  metricRegistry.register(MessageBuilder.metricRegistry)

  object Counters {
    val CandidateEndpoints: Counter = metricRegistry.counter("candidate", "endpoints")
    val ProcessedAudience: Counter = metricRegistry.counter("processed", "audience")
  }

  final case class Args(
    candidateMessage: List[String],
    candidateLog: List[String],
    messageDeliveryView: List[String],
    candidateContext: Option[String],
    contextModelNames: List[String],
    contextModelUris: List[String],
    candidateOutput: String,
    candidateContextOutput: String,
    candidateLogOutput: String,
    assignmentOutput: String,
    messageOutput: String,
    agents: Option[String],
    partition: PartitionArg,
    userSelectionInfo: Option[String],
    config: MessagingModuleConfig,
    productionCampaigns: Set[LadronCampaign],
    selectionInfoOutput: String
  )

  object Args {
    def parse(scioArgs: scio.Args): Args = Args(
      candidateMessage = scioArgs.list("candidateMessage"),
      candidateLog = scioArgs.list("candidateLog"),
      messageDeliveryView = scioArgs.list("messageDeliveryView"),
      candidateContext = scioArgs.optional("candidateContext"),
      contextModelNames = scioArgs.list("contextualModelNames"),
      contextModelUris = modelUris(scioArgs, scioArgs.list("contextualModelNames")),
      candidateOutput = scioArgs("candidateOutput"),
      candidateContextOutput = scioArgs("candidateContextOutput"),
      candidateLogOutput = scioArgs("candidateLogOutput"),
      assignmentOutput = scioArgs("assignmentOutput"),
      messageOutput = scioArgs("messageOutput"),
      partition = PartitionArg(scioArgs),
      userSelectionInfo = scioArgs.optional("userSelectionInfo"),
      agents = scioArgs.optional("agents"),
      config = MessagingModuleConfig.PerModuleConfig(
        MessagingModule.withNameInsensitive(scioArgs("module"))
      ),
      productionCampaigns = Campaigns.ProductionCampaigns,
      selectionInfoOutput = scioArgs("selectionInfoOutput")
    )

    def modelUris(scioArgs: scio.Args, modelNames: List[String]): List[String] =
      modelNames.map(name => scioArgs(s"contextual${name}Uri"))
  }

  // scalastyle:off
  def main(cmdArgs: Array[String]): Unit = {
    val (sc, scioArgs) = ContextAndArgs(cmdArgs)
    val args: Args = LadronConfig
      .check(Args.parse(scioArgs))
      .fold(error => throw new RuntimeException(error.description), identity)

    val candidateMessages = sc
      .avroFiles[CandidateMessage.Avro](args.candidateMessage)
      .withName("MapFromCandidateMessageAvro")
      .map(CandidateMessage.fromAvro)

    val candidateContexts: SCollection[CandidateContext] = args.candidateContext
      .map(sc.withName("Read tf.Example Context").tfRecordExampleFile(_))
      .getOrElse(sc.withName("Empty tf.Example context").empty[Example])
      .withName("toCandidateContext")
      .map { example =>
        CandidateContext
          .fromExample(example)
          .fold(error => throw new RuntimeException(error.description), identity)
      }

    val messageDeliveryView: SCollection[MessageHistory] = sc
      .avroFiles[MessageHistory.Avro](args.messageDeliveryView)
      .withName("MapFromMessageHistoryAvro")
      .map(MessageHistory.fromAvro)

    // UserSelectionInfo is optional,
    // in order to speed up hourly jobs (Activation) no UserSelectionInfo is supplied.
    val userWithContext: SCollection[UserWithContext] =
      args.userSelectionInfo
        .map(sc.withName("Read UserSelectionInfo").avroFile[UserSelectionInfo.Avro](_))
        .getOrElse(sc.empty[UserSelectionInfo.Avro])
        .withName("MapFromAvro")
        .map(UserSelectionInfo.fromAvro)
        .withName("toContextFreeContext")
        .map(usi => UserWithContext(usi.userId, usi.registrationCountry))

    // Agents are optional to allow running the pipeline until they have been computed.
    val agents: SCollection[ContextFreeModelByContext] =
      args.agents
        .map { agentSource =>
          sc.withName("Read ContextFreeBandits")
            .readBanditWithContext(agentSource)
        }
        .getOrElse {
          sc.withName("Empty ContextFreeBandits")
            .empty()
        }

    //get names and corresponding uris for the contextual models
    val contextualModels: Map[String, String] =
      (args.contextModelNames zip args.contextModelUris).toMap

    pipeline(args, SideOutput.Save)(
      candidateMessages,
      candidateContexts,
      messageDeliveryView,
      agents,
      userWithContext,
      contextualModels
    ).withName("MapToAvro")
      .map(Message.toAvro)
      .withName("WriteMessages")
      .saveAsAvroFile(args.messageOutput)

    sc.parallelize(Seq(parseLog(args.candidateLog)))
      .map(countIncludedCandidateEndpoints)
      .withName("WriteCandidateLog")
      .saveAsJsonFile(args.candidateLogOutput, numShards = 1)

    close(sc)
  }

  /**
   * Parse --candidateLog=endpoint:status into {endpoint:status}.
   */
  def parseLog(candidateLog: List[String]): Map[String, String] = {
    def split(s: String): (String, String) = {
      val index = s.lastIndexOf(':')
      s.substring(0, index) -> s.substring(index + 1)
    }
    candidateLog.map(split).toMap
  }

  private def countIncludedCandidateEndpoints(map: Map[String, String]): Map[String, String] = {
    val successes = map.count(_._2 == "Ok")
    Counters.CandidateEndpoints.inc(successes)
    map
  }

  def pipeline(
    args: Args,
    logSide: SideOutput
  )(
    candidates: SCollection[CandidateMessage],
    candidateContexts: SCollection[CandidateContext],
    history: SCollection[MessageHistory],
    agents: SCollection[ContextFreeModelByContext],
    userToCountry: SCollection[UserWithContext],
    contextualModels: Map[String, String]
  ): SCollection[Message] = {

    val id: SCollection[CandidateMessage] => SCollection[CandidateMessage] =
      identity

    id.andThen(
        _.filter(_.sendAt != DropImmediately)
          .accumulateCount(Counters.ProcessedAudience)
      )
      .andThen(_.transform("GroupByUser")(groupByUser))
      .andThen(
        _.transform("Assignment")(
          AssignmentJob.assignCandidates(
            args.partition.toDate,
            args.config.assignment,
            args.config.buckets,
            args.config.moduleBucketSalt
          )
        )
      )
      .andThen(
        _.transform("LogAssignment")(logSide.avroFile(assignmentToAvro)(args.assignmentOutput))
      )
      .andThen(
        _.transform("FilterAssignedCampaigns")(
          FilterAssignedCampaignJob.pipeline(args.productionCampaigns)(_)
        )
      )
      .andThen(
        _.transform("FilterRecent")(
          MessageHistoryFilterJob
            .pipeline(args.partition.toDate, args.config.campaigns, args.config.reSend)(_, history)
        )
      )
      .andThen(
        _.transform("MessageSelection")(
          MessageSelectionJob.selectCandidates(
            _,
            agents,
            contextualModels,
            args.config.optionByPolicy,
            PartitionString(args.partition.toPartitionString),
            args.config.module,
            userToCountry,
            candidateContexts
          )
        )
      )
      .andThen(
        _.transform("LogCandidateContexts")(logContexts(logSide, args.candidateContextOutput))
      )
      .andThen(_.transform("LogCandidates")(logSide.avroFile(matchedToAvro)(args.candidateOutput)))
      .andThen(logSelectionInfo(_, logSide, args.selectionInfoOutput))
      .andThen(_.transform("HackPushSendTime")(hackPushSend(args.partition.toDate)))
      .andThen(_.transform("MessageBuilder")(MessageBuilder.buildMessagesFromCandidates))
      .apply(candidates)
  }

  def groupByUser(
    candidates: SCollection[CandidateMessage]
  ): SCollection[CandidateMessages] = {
    candidates
      .groupBy(_.userId)
      .map {
        case (userId, cs) =>
          CandidateMessages(
            userId = userId,
            messages = cs.toList
          )
      }
  }

  def matchedToAvro(user: AssignedSelectedMessage): MatchedUser.Avro =
    MatchedUser.toAvro(
      MatchedUser(
        user.userId,
        user.selectionId,
        user.candidates
          .map(_.message)
          .map(candidate =>
            MatchedCampaign(candidate.channel, candidate.templateId, candidate.campaignId)
          )
      )
    )

  def assignmentToAvro(user: AssignedCandidateMessages): UserAssignment.Avro =
    UserAssignment.toAvro(UserAssignment(user.userId, user.group))

  def logSelectionInfo(
    messages: SCollection[AssignedSelectedMessage],
    logSide: SideOutput,
    path: String
  ): SCollection[AssignedSelectedMessage] = {
    logSide.avroFile(messageToSelectionInfoAvro)(path)(messages)
    messages
  }

  def messageToSelectionInfoAvro(message: AssignedSelectedMessage): SelectionInfo.Avro = {
    val recordId = message.selectedMessage.recordId
    val info = SelectionInfo(message.userId, message.explore, message.selectionId, recordId)
    SelectionInfo.toAvro(info)
  }

  def hackPushSend(
    date: LocalDate
  )(candidates: SCollection[AssignedSelectedMessage]): SCollection[AssignedSelectedMessage] = {
    candidates.map { c =>
      if (c.selectedMessage.channel == MessageChannel.Push) {
        c.selectedMessage.sendAt match {
          case SendAtDateTime(dateTime) if dateTime.toLocalDate == date =>
            hackPushSendLocalTime(c, dateTime)
          case _ => c
        }
      } else {
        c
      }
    }
  }

  def hackPushSendLocalTime(
    c: AssignedSelectedMessage,
    dateTime: LocalDateTime
  ): AssignedSelectedMessage =
    c.copy(selectedMessage = c.selectedMessage.copy(sendAt = SendAtTime(dateTime.toLocalTime)))

  def logContexts(logSide: SideOutput, path: String)(
    candidates: SCollection[AssignedSelectedMessage]
  ): SCollection[AssignedSelectedMessage] = {
    candidates
      .flatMap(contextToExamples)
      .transform("SaveCandidateContexts")(logSide.exampleFile(identity[Example])(path))

    candidates
  }

  def contextToExamples(candidate: AssignedSelectedMessage): Seq[Example] =
    candidate.candidates
      .map(_.context)
      .map(injectSelectionIdIntoExample(candidate.selectionId))

  def injectSelectionIdIntoExample(selectionId: SelectionId)(example: Example): Example = {
    val id: BytesList = BytesList.newBuilder
      .addValue(ByteString.copyFromUtf8(selectionId.id.value))
      .build()
    val feature: Feature = Feature.newBuilder().setBytesList(id).build()
    val exampleBuilder: Example.Builder = example.toBuilder
    exampleBuilder.getFeaturesBuilder.putFeature("selection_id", feature)
    exampleBuilder.build()
  }
}
