import NetworkExtension

/// A loopback "VPN" for paid builds. Mode connects to 10.7.0.1; every packet
/// is sent straight back with its IPv4 source and destination swapped, so the
/// phone's own pairing service sees the connection arrive from a peer, which
/// it accepts, instead of from the phone itself, which it refuses.
///
/// Only 10.7.0.1 is routed here; all other traffic bypasses the tunnel.
final class PacketTunnelProvider: NEPacketTunnelProvider {
    /// This end of the tunnel (the phone's address on it).
    private static let local = "10.7.1.1"
    /// The reflected "peer"; must match LoopbackVpn.PEER in engine/phone-ios.
    private static let peer = "10.7.0.1"

    override func startTunnel(options: [String: NSObject]?, completionHandler: @escaping (Error?) -> Void) {
        let ipv4 = NEIPv4Settings(addresses: [Self.local], subnetMasks: ["255.255.255.255"])
        ipv4.includedRoutes = [NEIPv4Route(destinationAddress: Self.peer, subnetMask: "255.255.255.255")]
        ipv4.excludedRoutes = [NEIPv4Route.default()]

        let settings = NEPacketTunnelNetworkSettings(tunnelRemoteAddress: Self.peer)
        settings.ipv4Settings = ipv4
        setTunnelNetworkSettings(settings) { [weak self] error in
            if error == nil { self?.reflect() }
            completionHandler(error)
        }
    }

    override func stopTunnel(with reason: NEProviderStopReason, completionHandler: @escaping () -> Void) {
        completionHandler()
    }

    /// Reads packets and writes them back with addresses swapped. Swapping the
    /// two addresses leaves the IP and TCP/UDP checksums valid (the sums are
    /// order-independent), so nothing else needs rewriting.
    private func reflect() {
        packetFlow.readPackets { [weak self] packets, protocols in
            guard let self else { return }
            var out = packets
            for i in out.indices where protocols[i].int32Value == AF_INET && out[i].count >= 20 {
                out[i].withUnsafeMutableBytes { raw in
                    let b = raw.bindMemory(to: UInt8.self)
                    for k in 0..<4 { b.swapAt(12 + k, 16 + k) }
                }
            }
            self.packetFlow.writePackets(out, withProtocols: protocols)
            self.reflect()
        }
    }
}
