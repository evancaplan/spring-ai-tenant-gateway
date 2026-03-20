package dev.evancaplan.spring_ai_tenant_gateway.tenant;

public class TenantContextHolder {
    private static final ThreadLocal<TenantContext> holder = new ThreadLocal<>();

    public static void set(TenantContext context) { holder.set(context); }
    public static TenantContext get() { return holder.get(); }
    public static void clear() { holder.remove(); }
}
