package furb;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
class Node {

    private final int port;

    private final int id;

    private final Runner runner;

    private final UDPServer udpServer;

    private final DatagramSocket sendSocket;

    private volatile boolean usingResource = false;

    private volatile boolean running = true;

    private volatile boolean isCoordinator = false;

    private Queue<Integer> queue = null;

    private final Random rnd = new Random();

    private final int PROCESSING_TIME_IN_SECONDS = 10;

    private final int WAIT_BETWEEN_USE_TRIES_IN_SECONDS = 20;

    public Node(int port, int id, Runner runner) throws Exception {
        this.port = port;
        this.id = id;
        this.runner = runner;

        this.sendSocket = new DatagramSocket();
        this.udpServer = new UDPServer(port, this::onPacket);

        new Thread(this::requestLoop, "node-req-" + id).start();
    }

    private void onPacket(DatagramPacket packet) {
        var message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();
        System.out.println("| " + id + " | recebeu: " + message + " de " + packet.getAddress() + ":" + packet.getPort());

        if (isCoordinator) {
            onPacketCoordinator(message);
            return;
        }

        if ("CONCESSAO".equals(message)) {
            startLocalProcessing();
        }
    }

    private void onPacketCoordinator(String message) {
        String[] parts = message.split("\\|");

        if (parts.length < 2) return;
        var type = parts[0].trim();
        var sender = parts[1].trim();

        int senderId = Integer.parseInt(sender);
        switch (type) {
            case "REQUISICAO":
                treatUseResourceRequisition(senderId);
                break;
            case "LIBERACAO":
                treatFree();
                break;
            default:
                System.out.println("Coordinator: tipo desconhecido: " + type);
        }
    }

    private void requestLoop() {
        while (running) {
            try {
                Thread.sleep(WAIT_BETWEEN_USE_TRIES_IN_SECONDS * 1000L);
                if (!running) break;

                if (!usingResource) {
                    int coordinatorPort = runner.getCoordinatorPort();
                    if (isSelfRequest(coordinatorPort)) {
                        handleSelfRequest();
                        return;
                    }
                    if (coordinatorPort > 0) {
                        sendMessage("REQUISICAO | " + id, coordinatorPort);
                        System.out.println("| " + id + " | enviou REQUISICAO");
                    } else {
                        System.out.println("| " + id + " | tentou enviar REQUISICAO mas não há coordenador");
                    }
                }
            } catch (InterruptedException ignored) {
                break;
            }
        }
    }

    private boolean isSelfRequest(int coordinatorPort) {
        return coordinatorPort == port;
    }

    private void freeResource() {
        if (!running) return;
        usingResource = false;
        int coordinatorPort = runner.getCoordinatorPort();

        if (isSelfRequest(coordinatorPort)) {
            treatFree();
        } else if (coordinatorPort > 0) {
            sendMessage("LIBERACAO | " + id, coordinatorPort);
            System.out.println("| " + id + " | liberou o recurso");
        }
    }

    private void sendMessage(String text, int toPort) {
        if (!running || sendSocket.isClosed()) {
            return;
        }

        try {
            byte[] data = text.getBytes(StandardCharsets.UTF_8);
            var packet = new DatagramPacket(data, data.length, InetAddress.getByName("127.0.0.1"), toPort);
            sendSocket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startLocalProcessing() {
        usingResource = true;
        new Thread(() -> {
            try {
                System.out.println("| " + id + " | está usando o recurso por " + PROCESSING_TIME_IN_SECONDS + "s");
                Thread.sleep(PROCESSING_TIME_IN_SECONDS * 1000L);
                freeResource();
            } catch (InterruptedException ignored) {}
        }, "node-process-" + id).start();
    }

    public synchronized void treatUseResourceRequisition(int nodeId) {
        Integer occupant = runner.getCriticalResource().getOccupant();

        handleOtherRequest(nodeId, occupant);
        System.out.println("Requisição pra usar o recurso tratada. Fila atual: " + queue);
    }

    private void handleSelfRequest() {
        Integer occupant = runner.getCriticalResource().getOccupant();
        if (occupant != null) {
            enqueueSelfIfNeeded();
            System.out.println("Requisição pra usar o recurso tratada. Fila atual: " + queue);
            return;
        }
        startProcessingForSelf();
    }

    private void enqueueSelfIfNeeded() {
        if (!queue.contains(id)) {
            queue.add(id);
            System.out.println("Coordinator: self-request enfileirada (recurso ocupado)");
        }
    }

    private void startProcessingForSelf() {
        runner.getCriticalResource().setOccupant(id);
        System.out.println("Coordinator: iniciando processamento local");
        startLocalProcessing();
    }

    private void handleOtherRequest(int nodeId, Integer occupant) {
        if (occupant != null) {
            if (queue.contains(nodeId)) {
                System.out.println("Coordinator: " + nodeId + " já está na fila, ignorando");
                return;
            }
            queue.add(nodeId);
            System.out.println("Coordinator: recurso ocupado por " + occupant + ". Enfileirando " + nodeId);
            return;
        }

        runner.getCriticalResource().setOccupant(nodeId);
        sendConcession(nodeId);
    }

    public synchronized void treatFree() {
        Integer next = queue.poll();
        if (next == null) {
            runner.getCriticalResource().setOccupant(null);
            System.out.println("Coordinator: recurso liberado, fila vazia.");
            return;
        }

        if (next == this.id) {
            runner.getCriticalResource().setOccupant(next);
            System.out.println("Coordinator: próximo é self -> iniciando processamento local");
            startLocalProcessing();
            return;
        }

        runner.getCriticalResource().setOccupant(next);
        sendConcession(next);
    }

    private void sendConcession(int nodeId) {
        int port = runner.getNodePort(nodeId);
        if (port <= 0) {
            System.out.println("sendConcession: porta inválida para node " + nodeId);
            runner.getCriticalResource().setOccupant(null);
            return;
        }

        String message = "CONCESSAO";
        try {
            byte[] data = message.getBytes(StandardCharsets.UTF_8);
            var packet = new DatagramPacket(data, data.length, InetAddress.getByName("127.0.0.1"), port);
            sendSocket.send(packet);
            System.out.println("Coordinator: enviou CONCESSAO para node " + nodeId + " (porta " + port + ")");
        } catch (IOException e) {
            e.printStackTrace();
            runner.getCriticalResource().setOccupant(null);
        }
    }

    public void promoteToCoordinator() {
        if (isCoordinator) return;
        isCoordinator = true;
        queue = new ConcurrentLinkedQueue<>();
        System.out.println("| " + id + " | agora é COORDENADOR");
    }

    public void shutdown() {
        running = false;
        udpServer.close();
        sendSocket.close();
        Thread.currentThread().interrupt();
        System.out.println("| " + id + " | encerrado");
    }
}
