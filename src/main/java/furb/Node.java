package furb;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class Node {

    private final int port;

    private final int id;

    private Coordinator coordinator;

    private UDPServer udpServer;

    private DatagramSocket socket;

    private boolean isUsingResource;


    public Node(int port, int id, Coordinator coordinator) {
        this.port = port;
        this.id = id;
        this.coordinator = coordinator;
        initiateUdpServer(port);
        listen();
        tryUseResource();
    }

    public void initiateUdpServer(int port) {
        try {
            this.socket = new DatagramSocket();
            this.udpServer = new UDPServer(port);
            System.out.println("iniciando server na porta " + port);
        } catch (Exception e) {
            System.out.println("Exception " + e.getMessage());
        }
    }

    private void tryUseResource() {
        try {
            while(true) {
                if (!isUsingResource) {
                    Thread.sleep(15 * 1000);
                    useResource();
                    Thread.sleep(5 * 1000);
                    return;
                }

                Thread.sleep(15 * 1000);
                freeResource();
            }
        } catch (Exception e) { }
    }

    public void listen() {
        new Thread(() -> {
            byte[] buffer = new byte[1024];

            while (true) {
                try {
                    var packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    var response = new String(packet.getData(), 0, packet.getLength());
                    if (response.equals("CONCESSAO")) {
                        isUsingResource = true;
                    }
                    System.out.println("Recebido: " + response + " de " + packet.getAddress());
                } catch (Exception e) {
                    e.printStackTrace();
                    break;
                }
            }
        }).start();
    }

    private void useResource() {
        var requisitionMessage = "REQUISICAO | " + id;
        sendMessage(requisitionMessage);
    }

    private void freeResource() {
        isUsingResource = false;
        var requisitionMessage = "LIBERACAO | " + id;
        sendMessage(requisitionMessage);
    }

    private void sendMessage(String requisitionMessage) {
        try {
            var address = InetAddress.getByName("127.0.0.1");

            byte[] data = requisitionMessage.getBytes();
            var packet = new DatagramPacket(data, data.length, address, coordinator.getPort());
            socket.send(packet);

            System.out.println("Enviado: " + requisitionMessage + " para porta " + coordinator.getPort());
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
