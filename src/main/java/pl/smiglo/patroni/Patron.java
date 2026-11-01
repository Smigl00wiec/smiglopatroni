package pl.smiglo.patroni;

import java.util.List;

public final class Patron {
    public final String id;
    public final int slot;
    public final int weight;
    public final String name;
    public final String headOwner;
    public final String headTexture;
    public final String godDesc;
    public final List<String> lore;

    public Patron(String id, int slot, int weight, String name, String headOwner,
                  String headTexture, String godDesc, List<String> lore) {
        this.id = id;
        this.slot = slot;
        this.weight = Math.max(1, weight);
        this.name = name;
        this.headOwner = headOwner;
        this.headTexture = headTexture;
        this.godDesc = godDesc;
        this.lore = lore;
    }
}
