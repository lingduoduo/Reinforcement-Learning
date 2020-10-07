import io
import numpy as np
import matplotlib.pyplot as plt
import tensorflow as tf

from ai_platform_example.config import config


def visualize(
    tensorboard_dir: str,
    stage: str,
    inputs: np.ndarray,
    targets: np.ndarray,
    outputs: np.ndarray,
    loss: float,
    step: int,
):
    file_writer = tf.summary.create_file_writer(tensorboard_dir)
    with file_writer.as_default():
        tf.summary.scalar(f'{stage}/loss', loss, step=step)
        tf.summary.image(
            f'{stage}/graphs', draw_graphs(inputs, targets, outputs), step=step
        )


def draw_graphs(inputs, targets, outputs):
    fig = plt.figure(figsize=(5, 5))
    width = int(np.ceil(np.sqrt(config.BATCH_SIZE)))
    for i in range(width ** 2)[: config.BATCH_SIZE]:
        plt.subplot(width, width, i + 1)

        plt.imshow(inputs[i, :, :, 0], cmap='gray')

        actual = np.argmax(targets[i])
        predicted = np.argmax(outputs[i])
        confidence = outputs[i][predicted]
        plt.xticks([])
        plt.yticks([])
        plt.xlabel(f'Actual: {actual}\nPred: {predicted} ({confidence:.2f})')

    plt.subplots_adjust(left=0, right=1, top=1, bottom=0.1, hspace=0.4)

    return plot_to_image(fig)


def plot_to_image(figure):
    buf = io.BytesIO()
    plt.savefig(buf, format='png')
    plt.close(figure)
    buf.seek(0)
    image = tf.image.decode_png(buf.getvalue(), channels=4)
    image = tf.expand_dims(image, 0)
    return image
