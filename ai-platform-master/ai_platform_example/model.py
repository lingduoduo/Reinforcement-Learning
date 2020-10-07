import tensorflow as tf
from tensorflow.keras import layers

from ai_platform_example.config import config
from ai_platform_example.data import N_CLASSES


class Model(tf.keras.Model):
    def __init__(self):
        super(Model, self).__init__(self)

        self.conv_layers = []
        for i in range(config.N_CONV_LAYERS):
            n_filters = config.N_CONV_FILTERS_LAYER_0 * int(
                (i + 1) ** config.CONV_FILTER_GROWTH
            )
            self.conv_layers.append(ConvLayer(n_filters))

        self.flatten = layers.Flatten()

        self.dense_layers = []
        for i in range(config.N_DENSE_LAYERS):
            self.dense_layer = layers.Dense(
                config.N_DENSE_UNITS, activation=config.DENSE_ACTIVATION
            )

        if config.DROPOUT_RATE > 0:
            self.dropout = layers.Dropout(config.DROPOUT_RATE)
        else:
            self.dropout = None

        self.final_layer = layers.Dense(N_CLASSES, activation='softmax')

    def call(self, inputs):
        x = inputs
        for layer in self.conv_layers:
            x = layer(x)
        x = self.flatten(x)
        for layer in self.dense_layers:
            x = layer(x)
        if self.dropout:
            x = self.dropout(x)
        x = self.final_layer(x)
        return x


class ConvLayer(tf.keras.Model):
    def __init__(self, n_filters):
        super(ConvLayer, self).__init__(self)

        self.conv = layers.Conv2D(
            n_filters,
            kernel_size=(config.CONV_KERNEL_SIZE, config.CONV_KERNEL_SIZE),
            activation=config.CONV_ACTIVATION,
        )
        self.pool = layers.MaxPooling2D(pool_size=config.POOL_SIZE)

    def call(self, inputs):
        x = self.conv(inputs)
        x = self.pool(x)
        return x
