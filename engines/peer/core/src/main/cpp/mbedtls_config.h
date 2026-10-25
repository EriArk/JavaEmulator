// SPDX-License-Identifier: GPL-3.0-only
#pragma once
// libdatachannel's DTLS backend references this extension even in data-only builds.
#define MBEDTLS_SSL_DTLS_SRTP
// Peer connections/certificate generation run on multiple upstream worker threads.
#define MBEDTLS_THREADING_C
#define MBEDTLS_THREADING_PTHREAD
