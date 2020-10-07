from typing import Tuple
import numpy as np
import tensorflow as tf
from tensorflow.keras.datasets import mnist

from ai_platform_example.config import config


N_CLASSES = 10
N_ROWS = 28
N_COLS = 28


def make_datasets() -> Tuple[tf.data.Dataset, tf.data.Dataset]:
    (train_images, train_labels), (test_images, test_labels) = mnist.load_data()

    def generator(images, labels):
        images = images.reshape(images.shape[0], N_ROWS, N_COLS, 1)
        images = (images / 255).astype(np.float32)
        labels = tf.keras.utils.to_categorical(labels, N_CLASSES)
        for image, label in zip(images, labels):
            yield image, label

    # Dataset.from_generator needs lambda
    train_generator = lambda: generator(train_images, train_labels)
    test_generator = lambda: generator(test_images, test_labels)

    train_ds = tf.data.Dataset.from_generator(
        train_generator, (tf.float32, tf.uint8), ((N_ROWS, N_COLS, 1), (N_CLASSES,))
    )
    test_ds = tf.data.Dataset.from_generator(
        test_generator, (tf.float32, tf.uint8), ((N_ROWS, N_COLS, 1), (N_CLASSES,))
    )

    train_ds = (
        train_ds.shuffle(config.N_SHUFFLE)
        .batch(config.BATCH_SIZE, drop_remainder=True)
        .repeat()
    )
    test_ds = test_ds.batch(config.BATCH_SIZE, drop_remainder=True)

    return train_ds, test_ds
