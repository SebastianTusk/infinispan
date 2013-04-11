package org.infinispan.interceptors.distribution;

import java.util.concurrent.Future;

import org.infinispan.commands.read.GetKeyValueCommand;
import org.infinispan.commands.tx.CommitCommand;
import org.infinispan.commands.tx.PrepareCommand;
import org.infinispan.commands.write.PutKeyValueCommand;
import org.infinispan.commands.write.WriteCommand;
import org.infinispan.context.InvocationContext;
import org.infinispan.context.impl.TxInvocationContext;
import org.infinispan.distribution.L1Manager;
import org.infinispan.factories.annotations.Inject;
import org.infinispan.factories.annotations.Start;
import org.infinispan.remoting.transport.jgroups.SuspectException;

public class L1TxInterceptor extends BaseDistributionInterceptor {
   
   private L1Manager l1Manager;
   private boolean isL1CacheEnabled;
   
   @Inject
   public void init (L1Manager l1Manager) {
      this.l1Manager = l1Manager;
   }

   @Start
   public void start() {
      isL1CacheEnabled = cacheConfiguration.clustering().l1().enabled();
   }

   @Override
   public Object visitPutKeyValueCommand(InvocationContext ctx, PutKeyValueCommand command) throws Throwable {
      SingleKeyRecipientGenerator skrg = new SingleKeyRecipientGenerator(command.getKey());
      Object returnValue = invokeNextInterceptor(ctx, command);
      // If this was a remote put record that which sent it
      if (isL1CacheEnabled && !ctx.isOriginLocal() && !skrg.generateRecipients().contains(ctx.getOrigin()))
         l1Manager.addRequestor(command.getKey(), ctx.getOrigin());

      return returnValue;
   }
   
   @Override
   public Object visitGetKeyValueCommand(InvocationContext ctx, GetKeyValueCommand command) throws Throwable {
      Object returnValue = invokeNextInterceptor(ctx, command);
      // If L1 caching is enabled, this is a remote command, and we found a value in our cache
      // we store it so that we can later invalidate it. If we did not find a value we still store it
      // because origin might put it with putForExternalRead.
      if (isL1CacheEnabled && !ctx.isOriginLocal()) l1Manager.addRequestor(command.getKey(), ctx.getOrigin());
      
      return returnValue;
   }
   
   @Override
   public Object visitCommitCommand(TxInvocationContext ctx, CommitCommand command) throws Throwable {
      Object returnValue;
      if (shouldInvokeRemoteTxCommand(ctx)) {
         Future<?> f = flushL1Caches(ctx);
         returnValue = invokeNextInterceptor(ctx, command);
         blockOnL1FutureIfNeeded(f);

      } else if (isL1CacheEnabled && !ctx.isOriginLocal() && !ctx.getLockedKeys().isEmpty()) {
         // We fall into this block if we are a remote node, happen to be the primary data owner and have locked keys.
         // it is still our responsibility to invalidate L1 caches in the cluster.
         blockOnL1FutureIfNeeded(flushL1Caches(ctx));
         returnValue = invokeNextInterceptor(ctx, command);
      }else{
         returnValue = invokeNextInterceptor(ctx, command);
      }
      return returnValue;
   }
   
   @Override
   public Object visitPrepareCommand(TxInvocationContext ctx, PrepareCommand command) throws Throwable {
      Object retVal = invokeNextInterceptor(ctx, command);

      if (isL1CacheEnabled && command.isOnePhaseCommit() && !ctx.getLockedKeys().isEmpty()) {
         flushL1Caches(ctx);
      }
      return retVal;
   }

   @Override
   protected Object handleWriteCommand(InvocationContext ctx,
         WriteCommand command, RecipientGenerator recipientGenerator,
         boolean skipRemoteGet, boolean skipL1Invalidation) throws Throwable {
      return invokeNextInterceptor(ctx, command);
   }
   
   private void blockOnL1FutureIfNeeded(Future<?> f) {
      if (f != null && cacheConfiguration.transaction().syncCommitPhase()) {
         try {
            f.get();
         } catch (Exception e) {
            // Ignore SuspectExceptions - if the node has gone away then there is nothing to invalidate anyway.
            if (!(e.getCause() instanceof SuspectException)) {
               getLog().failedInvalidatingRemoteCache(e);
            }
         }
      }
   }
      
   protected Future<?> flushL1Caches(InvocationContext ctx) {
      // TODO how do we tell the L1 manager which keys are removed and which keys may still exist in remote L1?
      return isL1CacheEnabled ? l1Manager.flushCacheWithSimpleFuture(ctx.getLockedKeys(), null, ctx.getOrigin(), true) : null;
   }
}
