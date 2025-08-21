package furb;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import lombok.Getter;

@Getter
public class Runner {

    private int port = 8080;

    private int nextNodeId = 0;

    private CriticalResource criticalResource;

    private final List<Node> nodes = new ArrayList<>();

    private Coordinator coordinator;


    public void start() {
        this.criticalResource = new CriticalResource(null);
        var firstCoordinator = new Coordinator(port, nextNodeId, this);
        coordinator = firstCoordinator;
        incrementPortAndNextNodeIdAndSleep();

        while(true) {
            var newNode = new Node(port, nextNodeId, firstCoordinator);
            nodes.add(newNode);
            incrementPortAndNextNodeIdAndSleep();
        }
    }

    private void incrementPortAndNextNodeIdAndSleep() {
        nextNodeId++;
        port++;
        sleepFortySeconds();
        killCoordinator();
    }

    private void sleepFortySeconds() {
        try {
            Thread.sleep(40 * 1000);
        } catch (InterruptedException i) {
            System.out.println("Thread interrompida " + i.getMessage());
        }
    }

    private void killCoordinator() {
        if(nodes.size() == 0) {
            return;
        }

        var newCoordinator = decideNewCoordinator();
        coordinator = new Coordinator(newCoordinator.getPort(), newCoordinator.getId(), this);
        newCoordinator = null;
        updateCoordinator(coordinator);
    }

    private Node decideNewCoordinator() {
        if(nodes.size() == 1) {
            return nodes.get(0);
        }

        var randomGenerator = new Random();
        var randomIndex = randomGenerator.nextInt(nodes.size());

        return nodes.get(randomIndex);

    }

    private void updateCoordinator(Coordinator coordinator) {
        for (Node node : nodes) {
            node.setCoordinator(coordinator);
        }
    }

    public int getNodePort(int nodeId) {
        for (Node node : nodes) {
            if (node.getId() == nodeId) {
                return node.getPort();
            }
        }

        return 1;
    }
}
