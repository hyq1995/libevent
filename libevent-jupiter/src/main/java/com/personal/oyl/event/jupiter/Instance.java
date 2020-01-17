package com.personal.oyl.event.jupiter;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * @author OuYang Liang
 */
public class Instance {
    private static final Logger log = LoggerFactory.getLogger(Instance.class);

    private AssignmentListener assignmentListener;

    public Instance(AssignmentListener assignmentListener) {
        this.assignmentListener = assignmentListener;
    }

    public void go(EventTransportMgr manager) throws InterruptedException, IOException, KeeperException {

        String instanceId = JupiterConfiguration.instance().uuid();
        log.info("Instance with id [" + instanceId + "] ready to start ......");

        ZkUtil.getInstance().initConnection(this, manager);
        log.info("Connection to zookeeper created successfully ......");

        try {
            ZkUtil.getInstance().createRoot(JupiterConfiguration.instance().getNameSpace());
        } catch (KeeperException e) {
            if (!e.code().equals(KeeperException.Code.NODEEXISTS)) {
                throw e;
            }
        }
        try {
            ZkUtil.getInstance().createRoot(JupiterConfiguration.instance().getWorkerNode());
        } catch (KeeperException e) {
            if (!e.code().equals(KeeperException.Code.NODEEXISTS)) {
                throw e;
            }
        }

        ZkUtil.getInstance().createWorkNode(JupiterConfiguration.instance().getWorkerNode(instanceId));
        log.info("Worker znode created successfully ......");

        String assignment = ZkUtil.getInstance().getContent(JupiterConfiguration.instance().getWorkerNode(instanceId), workWatcher);
        if (null != assignment && !assignment.trim().isEmpty()) {
            this.assignmentListener.onChange(assignment);
        }

        ZkUtil.getInstance().lock(instanceId, JupiterConfiguration.instance().getMasterNode());
        log.info("Now it is the master server...");
        log.info("perform the first check of the assignment, invoke method onChange()...");
        InstanceListener instanceListener = new InstanceListener();
        instanceListener.onChange();
    }

    private Watcher workWatcher = (event) -> {
        try {
            if (event.getType().equals(Watcher.Event.EventType.NodeDataChanged)) {
                String source = ZkUtil.getInstance().getContent(event.getPath(), this.workWatcher);
                this.assignmentListener.onChange(source);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    };
}