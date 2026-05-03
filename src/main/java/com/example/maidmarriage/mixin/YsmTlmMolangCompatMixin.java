package com.example.maidmarriage.mixin;

import com.example.maidmarriage.compat.YsmMolangActionBridge;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 往 YSM 已有的 `tlm.*` molang 注册表中追加我们模组自己的动作变量。
 *
 * <p>这里不把 YSM 当作编译依赖，而是：
 * 1. 用字符串目标类名挂 Mixin；
 * 2. 在注入点里用反射调用它的注册器；
 * 3. 用 JDK 动态代理实现 YSM 的 `eval(context)` 接口。
 *
 * <p>这样即使用户没装 YSM，也不会影响模组本体加载。
 */
@Pseudo
@Mixin(targets = "com.elfmcys.yesstevemodel.o000OoO0Oo0oo0OOOo0O0Oo0", remap = false)
public abstract class YsmTlmMolangCompatMixin {
    private static final String REGISTRY_CLASS = "com.elfmcys.yesstevemodel.o0o0oOO0o0O0o000Ooooo00o";
    private static final String EVAL_INTERFACE = "com.elfmcys.yesstevemodel.OoOo000ooOO0OOoOOoo0OOoo";
    private static final String CONTEXT_CLASS = "com.elfmcys.yesstevemodel.ooo00ooo0OOOo0O0oO0oo00O";
    private static final String REGISTER_METHOD = "Oooo0O0OO0O0000Oooo0Oo0o";
    private static final String CONTEXT_ENTITY_GETTER = "O0OOOoOooOO0OO0o00OoO0O0";

    @Inject(
            method = "O0OOOoOooOO0OO0o00OoO0O0(Lcom/elfmcys/yesstevemodel/o0o0oOO0o0O0o000Ooooo00o;)V",
            at = @At("TAIL")
    )
    private static void maidmarriage$registerExtraTlmVariables(@Coerce Object registry, CallbackInfo ci) {
        try {
            ClassLoader loader = registry.getClass().getClassLoader();
            Class<?> registryType = Class.forName(REGISTRY_CLASS, false, loader);
            Class<?> evalType = Class.forName(EVAL_INTERFACE, false, loader);
            Method register = registryType.getMethod(REGISTER_METHOD, String.class, evalType);

            register.invoke(registry, "maidmarriage_action", createEvaluator(loader, "action"));
            register.invoke(registry, "maidmarriage_hug", createEvaluator(loader, "hug"));
            register.invoke(registry, "maidmarriage_kiss", createEvaluator(loader, "kiss"));
            register.invoke(registry, "maidmarriage_pet", createEvaluator(loader, "pet"));
            register.invoke(registry, "maidmarriage_lift", createEvaluator(loader, "lift"));
            register.invoke(registry, "maidmarriage_action_time", createEvaluator(loader, "time"));
        } catch (Throwable ignored) {
        }
    }

    private static Object createEvaluator(ClassLoader loader, String kind) throws Exception {
        Class<?> evalType = Class.forName(EVAL_INTERFACE, false, loader);
        Class<?> contextType = Class.forName(CONTEXT_CLASS, false, loader);
        Method entityGetter = contextType.getMethod(CONTEXT_ENTITY_GETTER);

        InvocationHandler handler = (proxy, method, args) -> {
            if (!"eval".equals(method.getName()) || args == null || args.length != 1) {
                return null;
            }
            Object context = args[0];
            Object entity = entityGetter.invoke(context);
            if (!(entity instanceof EntityMaid maid)) {
                return defaultValue(kind);
            }
            return resolveValue(kind, maid);
        };
        return Proxy.newProxyInstance(loader, new Class<?>[]{evalType}, handler);
    }

    private static Object defaultValue(String kind) {
        return "action".equals(kind) ? 0 : ("time".equals(kind) ? 0.0D : Boolean.FALSE);
    }

    private static Object resolveValue(String kind, EntityMaid maid) {
        return switch (kind) {
            case "action" -> YsmMolangActionBridge.action(maid);
            case "hug" -> YsmMolangActionBridge.isHug(maid);
            case "kiss" -> YsmMolangActionBridge.isKiss(maid);
            case "pet" -> YsmMolangActionBridge.isPet(maid);
            case "lift" -> YsmMolangActionBridge.isLift(maid);
            case "time" -> YsmMolangActionBridge.actionTime(maid);
            default -> 0;
        };
    }
}
