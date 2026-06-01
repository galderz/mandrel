package com.oracle.svm.core.methodhandles;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "io.smallrye.config.ConfigMapping$NamingStrategy")
final class Target_ConfigMapping_NamingStrategy {
}

@TargetClass(className = "io.smallrye.config.ConfigMapping$BeanStyleGetters")
final class Target_ConfigMapping_BeanStyleGetters {
}

@TargetClass(className = "io.smallrye.config.ConfigMappingContext")
final class Target_io_smallrye_config_ConfigMappingContext {

    @Alias
    private Target_ConfigMapping_NamingStrategy namingStrategy;

    @Alias
    private Target_ConfigMapping_BeanStyleGetters beanStyleGetters;

    @Substitute
    public void applyNamingStrategy(Target_ConfigMapping_NamingStrategy ns) {
        System.err.println("DEBUG SUBSTITUTE: applyNamingStrategy called with: " + ns);
        if (ns != null) {
            this.namingStrategy = ns;
        }
    }

    @Substitute
    public void applyBeanStyleGetters(Target_ConfigMapping_BeanStyleGetters bsg) {
        System.err.println("DEBUG SUBSTITUTE: applyBeanStyleGetters called with: " + bsg);
        if (bsg != null) {
            this.beanStyleGetters = bsg;
        }
    }

    @Substitute
    public void applyPrefix(String prefix) {
        System.err.println("DEBUG SUBSTITUTE: applyPrefix called with: " + prefix);
        throw new RuntimeException("DEBUG SUBSTITUTE: applyPrefix reached — @TargetClass works for ConfigMappingContext");
    }
}
