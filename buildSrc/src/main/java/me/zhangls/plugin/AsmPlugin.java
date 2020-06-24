package me.zhangls.plugin;

import com.android.build.gradle.AppExtension;

import org.gradle.api.Plugin;
import org.gradle.api.Project;


/**
 * Asm Gralde Plugin
 */
public class AsmPlugin implements Plugin<Project> {

    /**
     * Apply this plugin to the given target object.
     *
     * @param project The target object
     */
    @Override
    public void apply(Project project) {
        //给com.android.application，注册一个transform
        AppExtension appExtension = project.getExtensions().findByType(AppExtension.class);
        appExtension.registerTransform(new AsmTransform());
    }
}
