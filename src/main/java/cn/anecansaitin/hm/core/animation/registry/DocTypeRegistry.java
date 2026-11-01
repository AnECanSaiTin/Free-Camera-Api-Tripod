package cn.anecansaitin.hm.core.animation.registry;

import cn.anecansaitin.hm.api.animation.registry.DocType;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

@EventBusSubscriber()
public class DocTypeRegistry {
    public static final ResourceKey<Registry<DocType<?>>> DOC_OBJECT_KEY = ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath("test", "doc_type"));
    private static final Registry<DocType<?>> docObjects = new RegistryBuilder<>(DOC_OBJECT_KEY).sync(true).create();

    @SubscribeEvent
    public static void newRegistry(NewRegistryEvent event) {
        event.register(docObjects);
    }

    static {
        DeferredRegister<DocType<?>> a = DeferredRegister.create(DOC_OBJECT_KEY, "a");
        Identifier cc = Identifier.parse("cc");
        docObjects.get
    }
}
