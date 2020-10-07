import os
import logging
import tensorflow as tf
import hypertune

from ai_platform_example.model import Model
from ai_platform_example.data import make_datasets
from ai_platform_example.config import config
from ai_platform_example.visualize import visualize


logger = tf.get_logger()
logger.setLevel(logging.INFO)


def main():
    config.parse_args()

    trial_path = os.path.join(config.JOB_ID, os.environ.get('CLOUD_ML_TRIAL_ID', '1'))
    logger.info('trial path: %s, config: %s', trial_path, str(config))

    train_ds, test_ds = make_datasets()

    optimizer = tf.keras.optimizers.RMSprop(config.LEARNING_RATE)
    model = Model()
    model.compile(optimizer=optimizer, loss=tf.keras.losses.categorical_crossentropy)

    tensorboard_dir = os.path.join(config.TENSORBOARD_DIR, trial_path)
    checkpoint_dir = os.path.join(config.CHECKPOINT_DIR, trial_path)
    for folder in [tensorboard_dir, checkpoint_dir]:
        if not folder.startswith('gs://') and not os.path.exists(folder):
            os.makedirs(folder)

    checkpoint_callback = tf.keras.callbacks.ModelCheckpoint(
        filepath=os.path.join(checkpoint_dir, 'model'),
        save_best_only=True,
        monitor='val_loss',
        verbose=1,
    )
    hypertune_callback = HyperTuneCallback()
    visualize_callback = VisualizeCallback(model, train_ds, test_ds, tensorboard_dir)
    early_stopping_callback = tf.keras.callbacks.EarlyStopping(
        monitor='val_loss',
        min_delta=config.EARLY_STOPPING_MIN_DELTA,
        patience=config.EARLY_STOPPING_PATIENCE,
        verbose=1,
    )

    model.fit(
        train_ds,
        validation_data=test_ds,
        callbacks=[
            checkpoint_callback,
            hypertune_callback,
            visualize_callback,
            early_stopping_callback,
        ],
        steps_per_epoch=config.STEPS_PER_EPOCH,
        validation_steps=5,
        verbose=0,
        epochs=config.EPOCHS,
    )


class HyperTuneCallback(tf.keras.callbacks.Callback):
    def __init__(self):
        self.hypertune = hypertune.HyperTune()
        self.hypertune_metric_tag = os.environ.get('CLOUD_ML_HP_METRIC_TAG', 'val_loss')

    def on_epoch_end(self, step, logs):
        self.hypertune.report_hyperparameter_tuning_metric(
            hyperparameter_metric_tag=self.hypertune_metric_tag,
            metric_value=logs['val_loss'],
            global_step=step,
        )


class VisualizeCallback(tf.keras.callbacks.Callback):
    def __init__(
        self,
        model: Model,
        train_ds: tf.data.Dataset,
        test_ds: tf.data.Dataset,
        tensorboard_dir: str,
    ):
        self.model = model
        self.train_inputs, self.train_targets = next(iter(train_ds))
        self.test_inputs, self.test_targets = next(iter(test_ds))
        self.tensorboard_dir = tensorboard_dir

    def on_epoch_end(self, step, logs):
        for stage, inputs, targets, loss in [
            ('train', self.train_inputs, self.train_targets, logs['loss']),
            ('test', self.test_inputs, self.test_targets, logs['val_loss']),
        ]:
            outputs = self.model.predict(inputs)
            visualize(self.tensorboard_dir, stage, inputs, targets, outputs, loss, step)


if __name__ == '__main__':
    main()
