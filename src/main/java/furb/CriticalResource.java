package furb;

class CriticalResource {

    private Integer occupant = null;


    public synchronized Integer getOccupant() {
        return occupant;
    }

    public synchronized void setOccupant(Integer id) {
        occupant = id;
    }
}

