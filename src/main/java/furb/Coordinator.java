package furb;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import lombok.Getter;

@Getter
class Coordinator {

    private final int port;

    private final int id;

    private final Runner runner;

    private final UDPServer udpServer;

    private final DatagramSocket sendSocket;

    private final Queue<Integer> queue = new ConcurrentLinkedQueue<>();


    public Coordinator(int port, int id, Runner runner) throws Exception {
        this.port = port;
        this.id = id;
        this.runner = runner;
        this.sendSocket = new DatagramSocket();
        this.udpServer = new UDPServer(port, this::onPacket);
        System.out.println("Coordenador " + id + " iniciou em porta " + port);
    }

    private void onPacket(DatagramPacket packet) {
        var message = new String(packet.getData(), 0, packet.getLength()).trim();
        System.out.println("Coordenador recebeu: " + message + " de " + packet.getAddress() + ":" + packet.getPort());

        String[] parts = message.split("\\|");
        if (parts.length < 2) return;
        var type = parts[0].trim();
        var senderId = parts[1].trim();

        try {
            int nodeId = Integer.parseInt(senderId);
            switch (type) {
                case "REQUISICAO":
                    treatRequisition(nodeId);
                    break;
                case "LIBERACAO":
                    treatFree();
                    break;
                default:
                    System.out.println("Tipo desconhecido: " + type);
            }
        } catch (NumberFormatException ignored) {}
    }

    private synchronized void treatRequisition(int nodeId) {
        var occupant = runner.getCriticalResource().getOccupant();
        if (occupant != null) {
            queue.add(nodeId);
            System.out.println("Coordinator: recurso ocupado por " + occupant + ". Enfileirando " + nodeId);
            return;
        }

        runner.getCriticalResource().setOccupant(nodeId);
        sendConcession(nodeId);
    }

    private synchronized void treatFree() {
        Integer nextNodeId = queue.poll();
        runner.getCriticalResource().setOccupant(nextNodeId);

        if (nextNodeId != null) {
            sendConcession(nextNodeId);
            return;
        }
    }

    private void sendConcession(int nodeId) {
        var port = runner.getNodePort(nodeId);
        if (port <= 0) return;

        var message = "CONCESSAO";
        try {
            var packet = new DatagramPacket(message.getBytes(), message.length(), InetAddress.getByName("127.0.0.1"), port);
            sendSocket.send(packet);
            System.out.println("Coordenador enviou CONCESSAO para node " + nodeId + " (porta " + port + ")");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void shutdown() {
        udpServer.close();
        sendSocket.close();
        System.out.println("Coordenador " + id + " encerrado");
    }
}

