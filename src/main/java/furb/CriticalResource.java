package furb;

/**
 * Representa o recurso crítico compartilhado entre os nós do sistema
 * distribuído.
 * Apenas um nó pode ocupar o recurso por vez, garantindo exclusão mútua.
 */
class CriticalResource {

    private Integer occupant = null;

    /**
     * Retorna o ID do nó que está atualmente usando o recurso crítico.
     * Método sincronizado para evitar condições de corrida.
     * 
     * @return ID do nó ocupante ou null se recurso está livre
     */
    public synchronized Integer getOccupant() {
        return occupant;
    }

    /**
     * Define qual nó está ocupando o recurso crítico.
     * Método sincronizado para garantir atomicidade na atribuição.
     * 
     * @param id ID do nó que irá ocupar o recurso (null para liberar)
     */
    public synchronized void setOccupant(Integer id) {
        occupant = id;
    }
}
