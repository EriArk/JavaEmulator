FROM eclipse-temurin:21-jdk-noble@sha256:114cabb73033ca4ce7ceec3deb62396bc1b716282bae051ed3c1d82e38fcab2d

SHELL ["/bin/bash", "-o", "pipefail", "-c"]
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates curl unzip zip git build-essential ninja-build nasm \
    python3 python-is-python3 pkg-config ccache && rm -rf /var/lib/apt/lists/*

ENV ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH=/opt/android-sdk/cmdline-tools/pinned/bin:/opt/android-sdk/build-tools/34.0.0:/opt/android-sdk/cmake/3.22.1/bin:$PATH
RUN curl --fail --location --retry 3 -o /tmp/tools.zip \
      https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip && \
    echo '4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583  /tmp/tools.zip' | sha256sum -c - && \
    mkdir -p /opt/android-sdk/cmdline-tools && unzip -q /tmp/tools.zip -d /tmp/tools && \
    mv /tmp/tools/cmdline-tools /opt/android-sdk/cmdline-tools/pinned && rm /tmp/tools.zip
# Headless SDK setup for this build environment; no SDK is redistributed in APKs.
RUN yes | sdkmanager --licenses >/dev/null; test ${PIPESTATUS[1]} -eq 0
RUN sdkmanager 'platforms;android-34' 'build-tools;34.0.0' \
    'cmake;3.22.1' 'ndk;22.1.7171670' 'ndk;28.2.13676358'
RUN sdkmanager 'platform-tools'
ENV GRADLE_USER_HOME=/cache/gradle HOME=/cache/home
WORKDIR /work
