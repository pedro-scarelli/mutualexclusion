package furb;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import lombok.Getter;

@Getter
public class Runner {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    private final List<Node> nodes = new ArrayList<>();

    private final ConcurrentHashMap<Integer,Integer> nodePorts = new ConcurrentHashMap<>();

    private final CriticalResource criticalResource = new CriticalResource();

    private final Random random = new Random();

    private volatile Integer coordinatorId = null;

    private volatile int coordinatorPort = -1;

    private int nextPort = 8080;

    private int nextNodeId = 1;

    public void start() throws Exception {
        createNode();

        var firstNode = nodes.get(0);
        firstNode.promoteToCoordinator();
        setCoordinator(firstNode.getId(), firstNode.getPort());

        scheduler.scheduleAtFixedRate(() -> {
            try { createNode(); } catch (Exception e) { e.printStackTrace(); }
        }, 40, 40, TimeUnit.SECONDS);

        scheduler.scheduleAtFixedRate(() -> {
            try { rotateCoordinator(); } catch (Exception e) { e.printStackTrace(); }
        }, 60, 60, TimeUnit.SECONDS);
    }

    private void createNode() throws Exception {
        var port = nextPort++;
        var id = nextNodeId++;
        var newNode = new Node(port, id, this);

        nodes.add(newNode);
        nodePorts.put(id, port);
        System.out.println("| " + id + " | criado na porta " + port);
    }

    private synchronized void rotateCoordinator() throws Exception {
        System.out.println("Matando coordenador");
        if (coordinatorId != null) {
            killCoordinator();
        }
        criticalResource.setOccupant(null);

        Node candidate = nodes.get(random.nextInt(nodes.size()));
        candidate.promoteToCoordinator();
        setCoordinator(candidate.getId(), candidate.getPort());

        System.out.println("Novo coordenador promovido: " + coordinatorId + " na porta " + coordinatorPort);
    }

    private void killCoordinator() {
        Node current = findNodeById(coordinatorId);
        if (current != null) {
            nodes.remove(current);
            nodePorts.remove(current.getId());
            current.shutdown();
        }
    }

    private Node findNodeById(int id) {
        for (Node node : nodes) {
            if (node.getId() == id) return node;
        }

        return null;
    }

    private void setCoordinator(int id, int port) {
        this.coordinatorId = id;
        this.coordinatorPort = port;
    }

    public int getNodePort(int nodeId) {
        return nodePorts.getOrDefault(nodeId, -1);
    }
}
