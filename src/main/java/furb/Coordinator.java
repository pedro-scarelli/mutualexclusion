package furb;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.LinkedList;
import java.util.Queue;

import lombok.Getter;

@Getter
public class Coordinator {

    private final int port;

    private final int id;

    private UDPServer udpServer;

    private DatagramSocket socket;

    private final Runner runner;

    private final Queue<Integer> criticalResourceQueue = new LinkedList<>();


    public Coordinator(int port, int id, Runner runner) {
        this.port = port;
        this.id = id;
        this.runner = runner;
        initiateUdpServer(port);
        listen();
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

    public void listen() {
        new Thread(() -> {
            byte[] buffer = new byte[1024];

            while (true) {
                try {
                    var packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    var response = new String(packet.getData(), 0, packet.getLength());
                    treatIncomingMessage(response);
                    System.out.println("Recebido: " + response + " de " + packet.getAddress());
                } catch (Exception e) {
                    e.printStackTrace();
                    break;
                }
            }
        }).start();
    }

    private void treatIncomingMessage(String message) {
        String [] messageParts = message.split("|");
        var messageType = messageParts[0].trim();
        switch (messageType) {
            case "REQUISICAO":
                treatRequisitionMessage(messageParts[1].trim());
                break;
            case "LIBERACAO":
                treatFreeResourceMessage(messageParts[1].trim());
                break;

            default:
                System.out.println("Tipo de mensagem não reconhecida: " + messageType);
                break;
        }
    }

    private void treatRequisitionMessage(String senderId) {
        var nodeId = Integer.valueOf(senderId);
        if(runner.getCriticalResource().getOccupant() != null) {
            criticalResourceQueue.add(nodeId);
            return;
        }

        runner.getCriticalResource().setOccupant(nodeId);
        sendDeliberationRequisition(runner.getNodePort(nodeId));
    }

    private void sendDeliberationRequisition(int port) {
        var requisitionMessage = "CONCESSAO";
        sendMessage(requisitionMessage, port);
    }

    private void sendMessage(String requisitionMessage, int port) {
        try {
            var address = InetAddress.getByName("127.0.0.1");

            byte[] data = requisitionMessage.getBytes();
            var packet = new DatagramPacket(data, data.length, address, port);
            socket.send(packet);

            System.out.println("Enviado: " + requisitionMessage + " para porta " + port);
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void treatFreeResourceMessage(String senderId) {
        var nextOccupant = criticalResourceQueue.poll();
        runner.getCriticalResource().setOccupant(nextOccupant);

        sendDeliberationRequisition(runner.getNodePort(Integer.valueOf(senderId)));
    }
}

