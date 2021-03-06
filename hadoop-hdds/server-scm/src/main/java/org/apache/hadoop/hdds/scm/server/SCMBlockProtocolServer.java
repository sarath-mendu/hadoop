/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license
 * agreements. See the NOTICE file distributed with this work for additional
 * information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache
 * License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the
 * License. You may obtain a
 * copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software
 * distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hdds.scm.server;

import com.google.protobuf.BlockingService;
import org.apache.hadoop.hdds.conf.OzoneConfiguration;
import org.apache.hadoop.hdds.protocol.proto.HddsProtos;
import org.apache.hadoop.hdds.protocol.proto.ScmBlockLocationProtocolProtos;
import org.apache.hadoop.hdds.scm.HddsServerUtil;
import org.apache.hadoop.hdds.scm.ScmInfo;
import org.apache.hadoop.hdds.scm.container.common.helpers.AllocatedBlock;
import org.apache.hadoop.hdds.scm.container.common.helpers.DeleteBlockResult;
import org.apache.hadoop.hdds.scm.exceptions.SCMException;
import org.apache.hadoop.hdds.scm.protocol.ScmBlockLocationProtocol;
import org.apache.hadoop.hdds.scm.protocolPB.ScmBlockLocationProtocolPB;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.ipc.ProtobufRpcEngine;
import org.apache.hadoop.ipc.RPC;
import org.apache.hadoop.ozone.common.BlockGroup;
import org.apache.hadoop.hdds.client.BlockID;
import org.apache.hadoop.ozone.common.DeleteBlockGroupResult;
import org.apache.hadoop.ozone.protocolPB
    .ScmBlockLocationProtocolServerSideTranslatorPB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import static org.apache.hadoop.hdds.scm.ScmConfigKeys
    .OZONE_SCM_BLOCK_CLIENT_ADDRESS_KEY;
import static org.apache.hadoop.hdds.scm.ScmConfigKeys
    .OZONE_SCM_HANDLER_COUNT_DEFAULT;
import static org.apache.hadoop.hdds.scm.ScmConfigKeys
    .OZONE_SCM_HANDLER_COUNT_KEY;
import static org.apache.hadoop.hdds.server.ServerUtils.updateRPCListenAddress;
import static org.apache.hadoop.hdds.scm.server.StorageContainerManager
    .startRpcServer;

/**
 * SCM block protocol is the protocol used by Namenode and OzoneManager to get
 * blocks from the SCM.
 */
public class SCMBlockProtocolServer implements ScmBlockLocationProtocol {
  private static final Logger LOG =
      LoggerFactory.getLogger(SCMBlockProtocolServer.class);

  private final StorageContainerManager scm;
  private final OzoneConfiguration conf;
  private final RPC.Server blockRpcServer;
  private final InetSocketAddress blockRpcAddress;

  /**
   * The RPC server that listens to requests from block service clients.
   */
  public SCMBlockProtocolServer(OzoneConfiguration conf,
      StorageContainerManager scm) throws IOException {
    this.scm = scm;
    this.conf = conf;
    final int handlerCount =
        conf.getInt(OZONE_SCM_HANDLER_COUNT_KEY,
            OZONE_SCM_HANDLER_COUNT_DEFAULT);

    RPC.setProtocolEngine(conf, ScmBlockLocationProtocolPB.class,
        ProtobufRpcEngine.class);
    // SCM Block Service RPC
    BlockingService blockProtoPbService =
        ScmBlockLocationProtocolProtos.ScmBlockLocationProtocolService
            .newReflectiveBlockingService(
                new ScmBlockLocationProtocolServerSideTranslatorPB(this));

    final InetSocketAddress scmBlockAddress = HddsServerUtil
        .getScmBlockClientBindAddress(conf);
    blockRpcServer =
        startRpcServer(
            conf,
            scmBlockAddress,
            ScmBlockLocationProtocolPB.class,
            blockProtoPbService,
            handlerCount);
    blockRpcAddress =
        updateRPCListenAddress(
            conf, OZONE_SCM_BLOCK_CLIENT_ADDRESS_KEY, scmBlockAddress,
            blockRpcServer);

  }

  public RPC.Server getBlockRpcServer() {
    return blockRpcServer;
  }

  public InetSocketAddress getBlockRpcAddress() {
    return blockRpcAddress;
  }

  public void start() {
    LOG.info(
        StorageContainerManager.buildRpcServerStartMessage(
            "RPC server for Block Protocol", getBlockRpcAddress()));
    getBlockRpcServer().start();
  }

  public void stop() {
    try {
      LOG.info("Stopping the RPC server for Block Protocol");
      getBlockRpcServer().stop();
    } catch (Exception ex) {
      LOG.error("Block Protocol RPC stop failed.", ex);
    }
    IOUtils.cleanupWithLogger(LOG, scm.getScmNodeManager());
  }

  public void join() throws InterruptedException {
    LOG.trace("Join RPC server for Block Protocol");
    getBlockRpcServer().join();
  }

  @Override
  public AllocatedBlock allocateBlock(long size, HddsProtos.ReplicationType
      type, HddsProtos.ReplicationFactor factor, String owner) throws
      IOException {
    return scm.getScmBlockManager().allocateBlock(size, type, factor, owner);
  }

  /**
   * Delete blocks for a set of object keys.
   *
   * @param keyBlocksInfoList list of block keys with object keys to delete.
   * @return deletion results.
   */
  @Override
  public List<DeleteBlockGroupResult> deleteKeyBlocks(
      List<BlockGroup> keyBlocksInfoList) throws IOException {
    LOG.info("SCM is informed by KSM to delete {} blocks", keyBlocksInfoList
        .size());
    List<DeleteBlockGroupResult> results = new ArrayList<>();
    for (BlockGroup keyBlocks : keyBlocksInfoList) {
      ScmBlockLocationProtocolProtos.DeleteScmBlockResult.Result resultCode;
      try {
        // We delete blocks in an atomic operation to prevent getting
        // into state like only a partial of blocks are deleted,
        // which will leave key in an inconsistent state.
        scm.getScmBlockManager().deleteBlocks(keyBlocks.getBlockIDList());
        resultCode = ScmBlockLocationProtocolProtos.DeleteScmBlockResult
            .Result.success;
      } catch (SCMException scmEx) {
        LOG.warn("Fail to delete block: {}", keyBlocks.getGroupID(), scmEx);
        switch (scmEx.getResult()) {
        case CHILL_MODE_EXCEPTION:
          resultCode = ScmBlockLocationProtocolProtos.DeleteScmBlockResult
              .Result.chillMode;
          break;
        case FAILED_TO_FIND_BLOCK:
          resultCode = ScmBlockLocationProtocolProtos.DeleteScmBlockResult
              .Result.errorNotFound;
          break;
        default:
          resultCode = ScmBlockLocationProtocolProtos.DeleteScmBlockResult
              .Result.unknownFailure;
        }
      } catch (IOException ex) {
        LOG.warn("Fail to delete blocks for object key: {}", keyBlocks
            .getGroupID(), ex);
        resultCode = ScmBlockLocationProtocolProtos.DeleteScmBlockResult
            .Result.unknownFailure;
      }
      List<DeleteBlockResult> blockResultList = new ArrayList<>();
      for (BlockID blockKey : keyBlocks.getBlockIDList()) {
        blockResultList.add(new DeleteBlockResult(blockKey, resultCode));
      }
      results.add(new DeleteBlockGroupResult(keyBlocks.getGroupID(),
          blockResultList));
    }
    return results;
  }

  @Override
  public ScmInfo getScmInfo() throws IOException {
    ScmInfo.Builder builder =
        new ScmInfo.Builder()
            .setClusterId(scm.getScmStorage().getClusterID())
            .setScmId(scm.getScmStorage().getScmId());
    return builder.build();
  }
}
