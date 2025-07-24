FROM openjdk:8

RUN apt-get update && apt-get upgrade -y && apt upgrade -y --without-new-pkgs && apt full-upgrade
RUN apt-get install -y build-essential wget \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*
# RUN apt-get install -y gcc python3 python3-pip && apt-get clean
# RUN pip uninstall -y setuptools && pip install --upgrade pip setuptools
RUN groupadd -g 1001 blendata
RUN useradd -r -m --home-dir /home/blendata --uid 1001 --gid 1001 --shell /bin/bash -p "$(openssl passwd -1 ubuntu)" blendata

ENV CONDA_DIR /opt/conda
RUN wget https://repo.anaconda.com/miniconda/Miniconda3-latest-Linux-x86_64.sh -O ~/miniconda.sh && \
    /bin/bash ~/miniconda.sh -b -p /opt/conda
ENV PATH=$CONDA_DIR/bin:$PATH


ENV ZEPPELIN_HOME=/opt/blendata/zeppelin
ENV ZEPPELIN_TMP_CONFIG_PATH=/opt/blendata/zeppelin/conf-tmp
ENV ZEPPELIN_POD_IP=127.0.0.1

WORKDIR ${ZEPPELIN_HOME}
COPY requirement.txt /opt/blendata/zeppelin

RUN pip install -r requirement.txt

# Env
ENV USE_HADOOP=true
ENV HADOOP_CONF_DIR=/opt/blendata/zeppelin
ENV SPARK_HOME=/opt/blendata/zeppelin/spark
ENV SPARK_SUBMIT_OPTIONS="--conf spark.ui.port=4040 --jars /opt/blendata/zeppelin/interpreter/spark/*"
ENV ZEPPELIN_LOG_DIR=/var/log/blendata/zeppelin
ENV ZEPPELIN_RUN_MODE=local
ENV PYTHONPATH=/usr/lib64/python3.6
ENV PYSPARK_DRIVER_PYTHON=python3.6
ENV PYSPARK_PYTHON=python3.6

COPY ./base_package/zeppelin ./
COPY ./base_package/docker-entrypoints ./
RUN chmod +x start-zeppelin-server-entrypoints.sh set-zeppelin-env.sh set-spark-env.sh

RUN mkdir -p ${ZEPPELIN_LOG_DIR}
RUN rm -rf /etc/localtime
RUN ln -s /usr/share/zoneinfo/Asia/Bangkok /etc/localtime
RUN ./set-zeppelin-env.sh
RUN ./set-spark-env.sh

EXPOSE 8998 4040
RUN chown -R blendata:blendata $ZEPPELIN_LOG_DIR $ZEPPELIN_HOME
USER blendata
ENTRYPOINT [ "./start-zeppelin-server-entrypoints.sh" ]
