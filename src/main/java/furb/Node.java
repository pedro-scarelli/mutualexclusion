package furb;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
class Node {

    private final int port;

    private final int id;

    private Coordinator coordinator;

    private final UDPServer udpServer;

    private final DatagramSocket sendSocket;

    private volatile boolean usingResource = false;

    private final int PROCESSING_TIME_IN_SECONDS = 10;

    private final int WAIT_BETWEEN_USE_TRIES_IN_SECONDS = 20;


    public Node(int port, int id, Coordinator coordinator) throws Exception {
        this.port = port;
        this.id = id;
        this.coordinator = coordinator;
        this.sendSocket = new DatagramSocket();
        this.udpServer = new UDPServer(port, this::onPacket);

        new Thread(this::requestLoop, "node-req-" + id).start();
    }

    private void onPacket(DatagramPacket packet) {
        var message = new String(packet.getData(), 0, packet.getLength()).trim();
        System.out.println("Node " + id + " recebeu: " + message + " de " + packet.getAddress() + ":" + packet.getPort());

        if ("CONCESSAO".equals(message)) {
            usingResource = true;
            new Thread(() -> {
                try {
                    System.out.println("Node " + id + " está usando o recurso por " + PROCESSING_TIME_IN_SECONDS + "s");
                    Thread.sleep(PROCESSING_TIME_IN_SECONDS * 1000L);
                    freeResource();
                } catch (InterruptedException ignored) {}
            }, "node-process-" + id).start();
        }
    }

    private void requestLoop() {
        while (true) {
            try {
                Thread.sleep(WAIT_BETWEEN_USE_TRIES_IN_SECONDS * 1000L);
                if (!usingResource) {
                    sendMessage("REQUISICAO | " + id, coordinator.getPort());
                    System.out.println("Node " + id + " enviou REQUISICAO");
                }
            } catch (InterruptedException ignored) {
                break;
            }
        }
    }

    private void freeResource() {
        usingResource = false;
        sendMessage("LIBERACAO | " + id, coordinator.getPort());
        System.out.println("Node " + id + " liberou o recurso");
    }

    private void sendMessage(String text, int toPort) {
        try {
            byte[] data = text.getBytes();
            var packet = new DatagramPacket(data, data.length, InetAddress.getByName("127.0.0.1"), toPort);
            sendSocket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
