package com.arthur.asteroid.contracts.v1;

/**
 * Logical type names carried in the Kafka {@code __TypeId__} header.
 *
 * <p>Both services map these aliases to concrete classes via
 * {@code spring.json.type.mapping}. That mapping is the point: with it, the
 * alias travels on the wire instead of the fully-qualified class name, so
 * either side can move or rename its Java packages without stranding messages
 * that are already in the topic. Widening {@code trusted.packages} to accept a
 * producer's FQCN would reintroduce exactly that coupling.
 */
public final class ContractTypes {

    /** Alias for {@link AsteroidCollisionEvent}. */
    public static final String ASTEROID_COLLISION_V1 = "asteroid-collision.v1";

    private ContractTypes() {
    }
}
