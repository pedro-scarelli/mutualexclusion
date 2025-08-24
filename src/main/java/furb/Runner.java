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

    private Coordinator coordinator;

    private int nextPort = 8080;

    private int nextNodeId = 1;


    public void start() throws Exception {
        coordinator = new Coordinator(nextPort++, nextNodeId++, this);

        createNode();

        scheduler.scheduleAtFixedRate(() -> {
            try { createNode(); } catch (Exception e) { e.printStackTrace(); }
        }, 40, 40, TimeUnit.SECONDS);

        scheduler.scheduleAtFixedRate(() -> {
            try { rotateCoordinator(); } catch (Exception e) { e.printStackTrace(); }
        }, 60, 60, TimeUnit.SECONDS);
    }

    private void createNode() throws Exception {
        var port = nextPort++;
        var newNode = new Node(port, nextNodeId, coordinator);
        nodes.add(newNode);

        System.out.println("Criado node " + nextNodeId + " na porta " + port);
        nextNodeId++;
    }

    private synchronized void rotateCoordinator() throws Exception {
        System.out.println("Matando coordenador");
        if (coordinator != null) coordinator.shutdown();

        var newPort = nextPort++;
        int newId;

        if (nodes.isEmpty()) {
            newId = nextNodeId++;
        } else {
            Node candidate = nodes.get(random.nextInt(nodes.size()));
            newId = candidate.getId();
        }
        coordinator = new Coordinator(newPort, newId, this);

        for (Node node : nodes) node.setCoordinator(coordinator);

        System.out.println("Novo coordenador: " + newId + " na porta " + newPort);
    }

    public int getNodePort(int nodeId) {
        return nodePorts.getOrDefault(nodeId, -1);
    }
}
