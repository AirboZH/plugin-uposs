package run.halo.uposs;

import org.pf4j.PluginWrapper;
import org.springframework.stereotype.Component;
import run.halo.app.plugin.BasePlugin;

/**
 * @author johnniang
 * @since 2.0.0
 */
@Component
public class UPOSSPlugin extends BasePlugin {

    public UPOSSPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }
}
