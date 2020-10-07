from setuptools import setup, find_packages

setup(
    name='ai-platform-example',
    description='Example of training on AI Platform with custom Docker container and hyperparameter tuning',
    version='0.1.0',
    packages=find_packages(exclude=['test', '*.test', '*.test.*']),
    include_package_data=True,
    install_requires=[
        'numpy==1.16.4',
        'scipy==1.3.0',
        'effortless_config>=0.6.1',
        'google-cloud-storage==1.16.1',
        'cloudml-hypertune',
        'matplotlib==3.1.1',
    ],
    extras_require={
        'cpu': [
            'tensorflow==2.0.0b1',
        ],
        'gpu': [
            'tensorflow-gpu==2.0.0b1',
        ],
    },
)
