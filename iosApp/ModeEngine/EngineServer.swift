import Foundation
import Network

/// The engine's wire: phone-harness's op protocol over plain HTTP/1.1.
///
///     GET  /ops                     -> {"ops": [...]}
///     POST /op {"op": ..., "kw": {}} -> {"result": ...}
///
/// Errors answer {"error": message}, with "unsupported": true when the op is
/// one this phone cannot do. Every request carries `Authorization: Bearer
/// <token>`. One request per connection; ops run one at a time on the main
/// thread, where XCUITest wants them.
final class EngineServer {
    typealias Handler = (_ op: String, _ kw: [String: Any]) throws -> Any?

    private let listener: NWListener
    private let token: String
    private let ops: [String]
    private let handler: Handler
    private let queue = DispatchQueue(label: "mode.engine.server")

    /// Set on the main thread whenever a request arrives; the idle watchdog reads it.
    private(set) var lastRequest = Date()

    init(port: UInt16, token: String, loopbackOnly: Bool, ops: [String], handler: @escaping Handler) throws {
        let parameters = NWParameters.tcp
        parameters.allowLocalEndpointReuse = true
        if loopbackOnly {
            parameters.requiredInterfaceType = .loopback
        }
        listener = try NWListener(using: parameters, on: NWEndpoint.Port(rawValue: port)!)
        self.token = token
        self.ops = ops
        self.handler = handler
    }

    func start() {
        listener.newConnectionHandler = { [weak self] connection in self?.accept(connection) }
        listener.start(queue: queue)
    }

    func stop() {
        listener.cancel()
    }

    private func accept(_ connection: NWConnection) {
        connection.start(queue: queue)
        receive(on: connection, buffer: Data())
    }

    private func receive(on connection: NWConnection, buffer: Data) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 1 << 20) { [weak self] data, _, isComplete, error in
            guard let self else { return }
            var buffer = buffer
            if let data { buffer.append(data) }
            if let request = HTTPRequest(parsing: buffer) {
                self.answer(request, on: connection)
            } else if isComplete || error != nil {
                connection.cancel()
            } else {
                self.receive(on: connection, buffer: buffer)
            }
        }
    }

    private func answer(_ request: HTTPRequest, on connection: NWConnection) {
        guard request.headers["authorization"] == "Bearer \(token)" else {
            return send(401, ["error": "missing or wrong engine token"], on: connection)
        }
        switch (request.method, request.path) {
        case ("GET", "/ops"):
            send(200, ["ops": ops], on: connection)
        case ("POST", "/op"):
            guard let body = try? JSONSerialization.jsonObject(with: request.body) as? [String: Any],
                  let op = body["op"] as? String else {
                return send(400, ["error": "body must be {\"op\": name, \"kw\": {...}}"], on: connection)
            }
            let kw = body["kw"] as? [String: Any] ?? [:]
            DispatchQueue.main.async {
                self.lastRequest = Date()
                let (status, reply) = self.run(op, kw)
                self.queue.async { self.send(status, reply, on: connection) }
            }
        default:
            send(404, ["error": "no such endpoint \(request.method) \(request.path)"], on: connection)
        }
    }

    private func run(_ op: String, _ kw: [String: Any]) -> (Int, [String: Any]) {
        do {
            let result = try handler(op, kw)
            return (200, ["result": result ?? NSNull()])
        } catch EngineError.unsupported(let message) {
            return (400, ["error": message, "unsupported": true])
        } catch EngineError.failed(let message) {
            return (500, ["error": message])
        } catch {
            return (500, ["error": "\(error)"])
        }
    }

    private func send(_ status: Int, _ body: [String: Any], on connection: NWConnection) {
        let payload = (try? JSONSerialization.data(withJSONObject: body)) ?? Data("{}".utf8)
        let head = "HTTP/1.1 \(status) \(status == 200 ? "OK" : "Error")\r\n"
            + "Content-Type: application/json\r\n"
            + "Content-Length: \(payload.count)\r\n"
            + "Connection: close\r\n\r\n"
        connection.send(content: Data(head.utf8) + payload, completion: .contentProcessed { _ in
            connection.cancel()
        })
    }
}

enum EngineError: Error {
    /// This phone cannot do that op; retrying will not help.
    case unsupported(String)
    /// The op ran and did not work; the message says what was on screen or what to do.
    case failed(String)
}

/// One HTTP request, parsed once the headers and the whole body have arrived.
struct HTTPRequest {
    let method: String
    let path: String
    let headers: [String: String]
    let body: Data

    init?(parsing data: Data) {
        guard let end = data.range(of: Data("\r\n\r\n".utf8)),
              let head = String(data: data[..<end.lowerBound], encoding: .utf8) else { return nil }
        var lines = head.components(separatedBy: "\r\n")
        let requestLine = lines.removeFirst().split(separator: " ")
        guard requestLine.count >= 2 else { return nil }

        var headers: [String: String] = [:]
        for line in lines {
            guard let colon = line.firstIndex(of: ":") else { continue }
            let name = line[..<colon].trimmingCharacters(in: .whitespaces).lowercased()
            headers[name] = line[line.index(after: colon)...].trimmingCharacters(in: .whitespaces)
        }
        let length = Int(headers["content-length"] ?? "") ?? 0
        let bodyStart = end.upperBound
        guard data.count - bodyStart >= length else { return nil }

        method = String(requestLine[0])
        path = String(requestLine[1])
        self.headers = headers
        body = data[bodyStart..<bodyStart + length]
    }
}
