package furb;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class UDPServer {
    private DatagramSocket socket;
    private Thread listenThread;

    public UDPServer(int port) throws Exception {
        socket = new DatagramSocket(port);

        listenThread = new Thread(this::listen);
        listenThread.start();
    }

    private void listen() {
        byte[] buffer = new byte[1024];

        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet); 
                String message = new String(packet.getData(), 0, packet.getLength());
                System.out.println("Recebido: " + message + " de " + packet.getAddress());
            } catch (Exception e) {
                e.printStackTrace();
                break;
            }
        }
    }
}

