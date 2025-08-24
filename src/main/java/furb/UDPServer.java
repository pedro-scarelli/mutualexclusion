package furb;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.function.Consumer;

class UDPServer {

    private final DatagramSocket socket;

    private final Thread listenThread;

    private final Consumer<DatagramPacket> onReceive;


    public UDPServer(int port, Consumer<DatagramPacket> onReceive) throws SocketException {
        this.socket = new DatagramSocket(port);
        this.onReceive = onReceive;
        this.listenThread = new Thread(this::listenLoop, "udp-listen-" + port);
        this.listenThread.start();
    }

    private void listenLoop() {
        byte[] buffer = new byte[2048];

        while (!socket.isClosed()) {
            try {
                var packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                // passa uma copia do pacote pra evitar racing no buffer
                byte[] dataCopy = new byte[packet.getLength()];
                System.arraycopy(packet.getData(), packet.getOffset(), dataCopy, 0, packet.getLength());
                var safePacket = new DatagramPacket(dataCopy, dataCopy.length, packet.getAddress(), packet.getPort());
                onReceive.accept(safePacket);
            } catch (SocketException se) {
                break;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void close() {
        socket.close();
        try {
            listenThread.join(500);
        } catch (InterruptedException ignored) {}
    }
}

