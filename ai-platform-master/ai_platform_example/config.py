from effortless_config import Config, setting


class config(Config):

    groups = ['local']

    BATCH_SIZE = 16
    N_SHUFFLE = 10000

    N_CONV_LAYERS = 3
    N_CONV_FILTERS_LAYER_0 = 16
    CONV_FILTER_GROWTH = 2.0
    CONV_ACTIVATION = 'relu'
    CONV_KERNEL_SIZE = 3
    POOL_SIZE = 2

    N_DENSE_LAYERS = 2
    N_DENSE_UNITS = 128
    DENSE_ACTIVATION = 'relu'

    DROPOUT_RATE = 0.25

    LEARNING_RATE = 0.001
    EARLY_STOPPING_MIN_DELTA = 0.0
    EARLY_STOPPING_PATIENCE = 10
    STEPS_PER_EPOCH = setting(100, local=10)
    EPOCHS = setting(10000, local=100)

    TENSORBOARD_DIR = setting(
        'gs://lingh/ai-platform-example/tensorboard',
        local='/tmp/ai-platform-example/tensorboard',
    )
    CHECKPOINT_DIR = setting(
        'gs://lingh/ai-platform-example/checkpoints',
        local='/tmp/ai-platform-example/checkpoints',
    )

    JOB_ID = setting(
        'unnamed-job',
        local='local',
    )
