# SSE Sampler for Apache JMeter

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![JMeter](https://img.shields.io/badge/JMeter-5.6%2B-red.svg)](https://jmeter.apache.org/)
[![Java](https://img.shields.io/badge/Java-11%2B-orange.svg)](https://openjdk.org/)

A high-performance **Server-Sent Events (SSE)** sampler plugin for Apache JMeter. Enables load testing of real-time streaming HTTP endpoints with full support for custom authentication headers, configurable listen duration, and accurate JMeter metrics reporting.

---

## Features

- ✅ Native SSE protocol support (`text/event-stream`)
- ✅ Custom request headers (e.g. `Authorization: Bearer <token>`)
- ✅ Configurable listen duration (seconds)
- ✅ Accurate JMeter metrics: Load Time, Connect Time, Latency, Bytes, Throughput
- ✅ HTTP error detection: 401, 403, 500 responses reported as failures
- ✅ Thread-safe shared `OkHttpClient` connection pool (no resource leaks)
- ✅ JMeter variable interpolation: `${myVar}`, `${__P(prop)}`
- ✅ Graceful shutdown — no socket leaks on test stop

---

## Installation

### Option 1 — JMeter Plugins Manager *(Recommended)*

1. Install [JMeter Plugins Manager](https://jmeter-plugins.org/install/Install/)
2. Open JMeter → **Options → Plugins Manager**
3. Search for **"SSE Sampler"**
4. Click **Apply Changes and Restart JMeter**

### Option 2 — Manual Installation

Download the latest `jmeter-sse-sampler-*.jar` from the [Releases](../../releases) page and copy it to your JMeter `lib/ext/` directory:

```bash
cp jmeter-sse-sampler-1.0.0.jar $JMETER_HOME/lib/ext/
```

Restart JMeter.

---

## Usage

1. Add a **Thread Group** to your test plan
2. Right-click Thread Group → **Add → Sampler → SSE Sampler**
3. Configure the sampler:

| Field | Description | Example |
|-------|-------------|---------|
| **SSE Endpoint URL** | Full URL of the SSE endpoint | `https://api.example.com/events` |
| **Listen Duration (seconds)** | How long each thread listens for events | `30` |
| **Custom Request Headers** | One `Name: Value` pair per line | `Authorization: Bearer ${token}` |

### Custom Headers Example

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
X-Tenant-Id: acme-corp
X-Request-Id: ${__UUID()}
```

---

## JMeter Metrics

After each sample, the following metrics are reported to JMeter:

| Metric | Description |
|--------|-------------|
| **Load Time** | Total duration the sampler listened for events |
| **Connect Time** | Time until the first HTTP response headers were received |
| **Response Code** | HTTP status code (`200`, `401`, `500`, etc.) |
| **Response Message** | `OK — 42 events received` or error description |
| **Response Body** | All received SSE events in `ID | Type | Data` format |
| **Bytes** | Total bytes of received event data |

---

## Building from Source

### Prerequisites

- Java 11+
- Maven 3.6+

### Build

```bash
git clone https://github.com/YOUR_USERNAME/jmeter-sse-sampler.git
cd jmeter-sse-sampler
mvn clean package
```

The shaded (fat) JAR will be created at:
```
target/jmeter-sse-sampler-1.0.0.jar
```

This JAR bundles all required dependencies (OkHttp, Kotlin stdlib) and can be dropped directly into JMeter's `lib/ext/`.

### Dependencies

| Dependency | Version | Scope |
|------------|---------|-------|
| `com.squareup.okhttp3:okhttp` | 5.3.2 | bundled |
| `com.squareup.okhttp3:okhttp-sse` | 5.3.2 | bundled |
| `org.apache.jmeter:ApacheJMeter_core` | 5.6.3 | provided |

---

## Architecture Notes

### Thread Safety

The `OkHttpClient` is shared across all JMeter threads via a `static final` field. This means all virtual users share a single connection pool, which is the correct OkHttp usage pattern for high concurrency.

### Graceful Cancellation

When the listen duration expires, the plugin:
1. Sets an `intentionalCancel` flag
2. Calls `eventSource.cancel()` to close the socket
3. OkHttp's resulting `onFailure` callback is suppressed via the flag
4. All JMeter result fields are written on the JMeter thread (no race conditions)

### Logging

- `log.debug` — per-event logging (disabled by default in JMeter)
- `log.info` — per-sample summary (connection open/close, event count)
- `log.error` — actual failures only

To enable debug logging, add to `$JMETER_HOME/bin/log4j2.xml`:
```xml
<Logger name="io.github.cuneytcakir.jmeter.sse" level="debug"/>
```

---

## Contributing

Pull requests are welcome! Please open an issue first to discuss what you would like to change.

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Commit your changes (`git commit -m 'Add my feature'`)
4. Push to the branch (`git push origin feature/my-feature`)
5. Open a Pull Request

---

## License

This project is licensed under the [Apache License 2.0](LICENSE).

---

## Acknowledgements

- Built with [OkHttp](https://square.github.io/okhttp/) by Square
- Inspired by the [JMeter Plugins](https://jmeter-plugins.org/) ecosystem
