package com.mapmory.backend.common.cache;

import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.stereotype.Component;

@Component
@WebEndpoint(id = "deploymentcache")
public class DeploymentCacheEndpoint {

    private final DeploymentCacheControl control;

    public DeploymentCacheEndpoint(DeploymentCacheControl control) {
        this.control = control;
    }

    @ReadOperation
    public DeploymentCacheControl.Snapshot status() {
        return control.snapshot();
    }

    @WriteOperation
    public DeploymentCacheControl.Snapshot changeMode(DeploymentCacheControl.Mode mode) {
        return control.changeMode(mode);
    }
}
