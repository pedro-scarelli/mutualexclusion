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

/**
 * Representa um nó no sistema distribuído que compete pelo recurso crítico.
 * Cada nó pode atuar como cliente (solicitando recurso) ou coordenador
 * (gerenciando acesso).
 */

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
        System.out
                .println("| " + id + " | recebeu: " + message + " de " + packet.getAddress() + ":" + packet.getPort());

        // Se é coordenador, processa mensagens de controle
        if (isCoordinator) {
            onPacketCoordinator(message);
            return;
        }

        // Se é cliente e recebe CONCESSÃO, pode usar o recurso
        if ("CONCESSAO".equals(message)) {
            startLocalProcessing();
        }
    }

    /**
     * Processa mensagens recebidas quando atua como coordenador
     */
    private void onPacketCoordinator(String message) {
        String[] parts = message.split("\\|");

        if (parts.length < 2)
            return;
        var type = parts[0].trim();
        var sender = parts[1].trim();

        int senderId = Integer.parseInt(sender);
        switch (type) {
            case "REQUISICAO":
                // Nó quer usar o recurso
                treatUseResourceRequisition(senderId);
                break;
            case "LIBERACAO":
                // Nó terminou de usar o recurso
                treatFree();
                break;
            default:
                System.out.println("Coordinator: tipo desconhecido: " + type);
        }
    }

    /**
     * Thread que tenta requisitar o uso do recurso crítico
     */
    private void requestLoop() {
        while (running) {
            try {
                Thread.sleep(WAIT_BETWEEN_USE_TRIES_IN_SECONDS * 1000L);
                if (!running)
                    break;

                // Se não está usando o recurso, tenta requisitar
                if (!usingResource) {
                    int coordinatorPort = runner.getCoordinatorPort();

                    // Se o próprio nó é o coordenador
                    if (isSelfRequest(coordinatorPort)) {
                        handleSelfRequest();
                        continue;
                    }

                    // Envia requisição para o coordenador
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

    /**
     * Verifica se a requisição é do próprio nó (quando ele é coordenador)
     */
    private boolean isSelfRequest(int coordinatorPort) {
        return coordinatorPort == port;
    }

    /**
     * Libera o recurso crítico após terminar o processamento
     */
    private void freeResource() {
        if (!running)
            return;
        usingResource = false;
        int coordinatorPort = runner.getCoordinatorPort();

        // Se próprio nó é coordenador, processa diretamente
        if (isSelfRequest(coordinatorPort)) {
            treatFree();
        } else if (coordinatorPort > 0) {
            // Notifica coordenador que liberou o recurso
            sendMessage("LIBERACAO | " + id, coordinatorPort);
            System.out.println("| " + id + " | liberou o recurso");
        }
    }

    /**
     * Envia mensagem UDP para outro nó
     */
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

    /**
     * Inicia o processamento local do recurso crítico
     * Simula uso do recurso por um tempo determinado
     */
    private void startLocalProcessing() {
        usingResource = true;
        new Thread(() -> {
            try {
                System.out.println("| " + id + " | está usando o recurso por " + PROCESSING_TIME_IN_SECONDS + "s");
                Thread.sleep(PROCESSING_TIME_IN_SECONDS * 1000L);
                freeResource(); // Libera após o tempo
            } catch (InterruptedException ignored) {
            }
        }, "node-process-" + id).start();
    }

    /**
     * COORDENADOR: Trata requisição de uso do recurso por outro nó
     */
    public synchronized void treatUseResourceRequisition(int nodeId) {
        Integer occupant = runner.getCriticalResource().getOccupant();

        handleOtherRequest(nodeId, occupant);
        System.out.println("Requisição pra usar o recurso tratada. Fila atual: " + queue);
    }

    /**
     * COORDENADOR: Trata requisição do próprio nó (auto-requisição)
     */
    private void handleSelfRequest() {
        Integer occupant = runner.getCriticalResource().getOccupant();
        // Se recurso ocupado, enfileira a si mesmo
        if (occupant != null) {
            enqueueSelfIfNeeded();
            System.out.println("Requisição pra usar o recurso tratada. Fila atual: " + queue);
            return;
        }
        // Se livre, inicia processamento imediatamente
        startProcessingForSelf();
    }

    /**
     * COORDENADOR: Adiciona próprio nó à fila se necessário
     */
    private void enqueueSelfIfNeeded() {
        if (!queue.contains(id)) {
            queue.add(id);
            System.out.println("Coordinator: self-request enfileirada (recurso ocupado)");
        }
    }

    /**
     * COORDENADOR: Inicia processamento para si mesmo
     */
    private void startProcessingForSelf() {
        runner.getCriticalResource().setOccupant(id);
        System.out.println("Coordinator: iniciando processamento local");
        startLocalProcessing();
    }

    /**
     * COORDENADOR: Processa requisição de outro nó
     */
    private void handleOtherRequest(int nodeId, Integer occupant) {
        // Se recurso ocupado, enfileira o requisitante
        if (occupant != null) {
            if (queue.contains(nodeId)) {
                System.out.println("Coordinator: " + nodeId + " já está na fila, ignorando");
                return;
            }
            queue.add(nodeId);
            System.out.println("Coordinator: recurso ocupado por " + occupant + ". Enfileirando " + nodeId);
            return;
        }

        // Se recurso livre, concede imediatamente
        runner.getCriticalResource().setOccupant(nodeId);
        sendConcession(nodeId);
    }

    /**
     * COORDENADOR: Trata liberação do recurso e atende próximo da fila
     */
    public synchronized void treatFree() {
        Integer next = queue.poll(); // Remove próximo da fila

        // Se fila vazia, recurso fica livre
        if (next == null) {
            runner.getCriticalResource().setOccupant(null);
            System.out.println("Coordinator: recurso liberado, fila vazia.");
            return;
        }

        // Se próximo é o próprio coordenador
        if (next == this.id) {
            runner.getCriticalResource().setOccupant(next);
            System.out.println("Coordinator: próximo é self -> iniciando processamento local");
            startLocalProcessing();
            return;
        }

        // Concede recurso ao próximo nó da fila
        runner.getCriticalResource().setOccupant(next);
        sendConcession(next);
    }

    /**
     * COORDENADOR: Envia mensagem de CONCESSÃO para um nó
     */
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

    /**
     * Promove este nó para coordenador do sistema
     */
    public void promoteToCoordinator() {
        if (isCoordinator)
            return;
        isCoordinator = true;
        queue = new ConcurrentLinkedQueue<>(); // Inicializa fila de requisiçõe
        System.out.println("| " + id + " | agora é COORDENADOR");
    }

    /**
     * Encerra o nó, fechando todas as conexões e threads
     */
    public void shutdown() {
        running = false;
        udpServer.close();
        sendSocket.close();
        Thread.currentThread().interrupt();
        System.out.println("| " + id + " | encerrado");
    }
}
