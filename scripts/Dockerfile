# Linux ARM de 32 bits con gcc, para ensamblar y correr los .s del compilador.
FROM --platform=linux/arm/v7 debian:bookworm-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends gcc libc6-dev \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /trabajo
