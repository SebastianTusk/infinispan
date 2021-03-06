/* 
 * JBoss, Home of Professional Open Source 
 * Copyright 2013 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tag. All rights reserved. 
 * See the copyright.txt in the distribution for a 
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use, 
 * modify, copy, or redistribute it subject to the terms and conditions 
 * of the GNU Lesser General Public License, v. 2.1. 
 * This program is distributed in the hope that it will be useful, but WITHOUT A 
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details. 
 * You should have received a copy of the GNU Lesser General Public License, 
 * v.2.1 along with this distribution; if not, write to the Free Software 
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, 
 * MA  02110-1301, USA.
 */
package org.infinispan.jcache;

import javax.cache.Cache;

import org.infinispan.jcache.logging.Log;
import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryCreated;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryModified;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryRemoved;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryVisited;
import org.infinispan.notifications.cachelistener.event.CacheEntryCreatedEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryModifiedEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryRemovedEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryVisitedEvent;
import org.infinispan.util.logging.LogFactory;

/**
 * Adapts Infinispan notification mechanism to JSR 107 requirements.
 *
 * @author Vladimir Blagojevic
 * @author Galder Zamarreño
 * @since 5.3
 */
@Listener
public class JCacheListenerAdapter<K, V> {

   private static final Log log =
         LogFactory.getLog(JCacheListenerAdapter.class, Log.class);

   private static final boolean isTrace = log.isTraceEnabled();

   private final Cache<K, V> cache;
   private final JCacheNotifier<K, V> notifier;

   public JCacheListenerAdapter(Cache<K, V> cache, JCacheNotifier<K, V> notifier) {
      this.cache = cache;
      this.notifier = notifier;
   }

   @CacheEntryCreated
   @SuppressWarnings("unused")
   public void handleCacheEntryCreatedEvent(CacheEntryCreatedEvent<K, V> e) {
      // JCache listeners notified only once, so do it after the event
      if (!e.isPre())
         notifier.notifyEntryCreated(cache, e.getKey(), e.getValue());
   }

   @CacheEntryModified
   @SuppressWarnings("unused")
   public void handleCacheEntryModifiedEvent(CacheEntryModifiedEvent<K, V> e) {
      // JCache listeners notified only once, so do it after the event
      if (!e.isPre() && !e.isCreated())
         notifier.notifyEntryUpdated(cache, e.getKey(), e.getValue());
   }

   @CacheEntryRemoved
   @SuppressWarnings("unused")
   public void handleCacheEntryRemovedEvent(CacheEntryRemovedEvent<K, V> e) {
      // JCache listeners notified only once, so do it after the event
      if (!e.isPre())
         notifier.notifyEntryRemoved(cache, e.getKey(), e.getOldValue());
   }

   @CacheEntryVisited
   @SuppressWarnings("unused")
   public void handleCacheEntryVisitedEvent(CacheEntryVisitedEvent<K, V> e) {
      // JCache listeners notified only once, so do it after the event
      if (!e.isPre())
         notifier.notifyEntryRead(cache, e.getKey(), e.getValue());
   }

}
