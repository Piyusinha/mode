import Foundation
import Network

/// Listens on 127.0.0.1 for the one request "Continue with ChatGPT" sends back when sign-in finishes
/// (OpenAI's open-source sign-in only allows an HTTP loopback redirect). Answers with a short page,
/// hands the URL over, and stops.
final class LoopbackServer: @unchecked Sendable {
    private var listener: NWListener?
    private let queue = DispatchQueue(label: "mode.loopback")

    func start(port: UInt16, onCallback: @escaping @MainActor (URL) -> Void) throws {
        stop()
        let parameters = NWParameters.tcp
        parameters.requiredLocalEndpoint = .hostPort(host: "127.0.0.1", port: NWEndpoint.Port(rawValue: port)!)
        parameters.allowLocalEndpointReuse = true
        let listener = try NWListener(using: parameters)
        listener.newConnectionHandler = { [weak self] connection in self?.serve(connection, port: port, onCallback: onCallback) }
        listener.start(queue: queue)
        self.listener = listener
    }

    func stop() {
        listener?.cancel()
        listener = nil
    }

    private func serve(_ connection: NWConnection, port: UInt16, onCallback: @escaping @MainActor (URL) -> Void) {
        connection.start(queue: queue)
        connection.receive(minimumIncompleteLength: 1, maximumLength: 65_536) { [weak self] data, _, _, _ in
            let request = data.flatMap { String(data: $0, encoding: .utf8) } ?? ""
            // "GET /auth/callback?code=…&state=… HTTP/1.1"
            let target = request.split(separator: "\r\n").first?.split(separator: " ").dropFirst().first.map(String.init)
            let page = "<html><body style=\"font-family:-apple-system;text-align:center;padding-top:30vh\">"
                + "<h2>Signed in</h2><p>Return to Mode.</p></body></html>"
            let response = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: \(page.utf8.count)\r\nConnection: close\r\n\r\n\(page)"
            connection.send(content: response.data(using: .utf8), completion: .contentProcessed { _ in connection.cancel() })
            guard let target, target.contains("code=") || target.contains("error="),
                  let url = URL(string: "http://127.0.0.1:\(port)\(target)") else { return }
            self?.stop()
            Task { @MainActor in onCallback(url) }
        }
    }
}
