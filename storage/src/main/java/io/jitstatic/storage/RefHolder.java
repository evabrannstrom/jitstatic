package io.jitstatic.storage;

/*-
 * #%L
 * jitstatic
 * %%
 * Copyright (C) 2017 - 2018 H.Hegardt
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.lib.ObjectLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spencerwi.either.Either;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.jitstatic.CommitMetaData;
import io.jitstatic.JitStaticConstants;
import io.jitstatic.MetaData;
import io.jitstatic.auth.UserData;
import io.jitstatic.hosted.FailedToLock;
import io.jitstatic.hosted.KeyAlreadyExist;
import io.jitstatic.hosted.LoadException;
import io.jitstatic.hosted.RefHolderLock;
import io.jitstatic.hosted.StoreInfo;
import io.jitstatic.source.LargeObjectStreamProvider;
import io.jitstatic.source.ObjectStreamProvider;
import io.jitstatic.source.SmallObjectStreamProvider;
import io.jitstatic.source.Source;
import io.jitstatic.source.SourceInfo;
import io.jitstatic.utils.LinkedException;
import io.jitstatic.utils.Pair;
import io.jitstatic.utils.ShouldNeverHappenException;
import io.jitstatic.utils.VersionIsNotSame;
import io.jitstatic.utils.WrappingAPIException;
import io.jitstatic.utils.Functions.ThrowingSupplier;

@SuppressFBWarnings(value = "NP_OPTIONAL_RETURN_NULL", justification = "Map's returns null and there's a difference from a previous cached 'not found' value and a new 'not found'")
public class RefHolder implements RefHolderLock {

    private static final Logger LOG = LoggerFactory.getLogger(RefHolder.class);
    private volatile Map<String, Either<Optional<StoreInfo>, Pair<String, UserData>>> refCache;
    private final ReentrantReadWriteLock refLock = new ReentrantReadWriteLock(true);
    private final Map<String, Thread> activeKeys = new ConcurrentHashMap<>();
    private final String ref;
    private final Source source;
    private final int threshold;

    public RefHolder(final String ref, final Source source) {
        this.ref = ref;
        this.refCache = getStorage(1000);
        this.source = source;
        threshold = 1_000_000;
    }

    private LinkedHashMap<String, Either<Optional<StoreInfo>, Pair<String, UserData>>> getStorage(final int size) {
        return new LinkedHashMap<>(size) {
            private static final long serialVersionUID = 1L;

            protected boolean removeEldestEntry(final Map.Entry<String, Either<Optional<StoreInfo>, Pair<String, UserData>>> eldest) {
                return size() > 1000;
            };
        };
    }

    public Optional<StoreInfo> getKey(final String key) {
        final Either<Optional<StoreInfo>, Pair<String, UserData>> data = read(() -> refCache.get(key));
        if (data != null && data.isLeft()) {
            return data.getLeft();
        }
        return null;
    }

    public void putKey(final String key, final Optional<StoreInfo> store) {
        write(() -> refCache.put(key, Either.left(store)));
    }

    public <T> Either<T, FailedToLock> lockWrite(final Supplier<T> supplier, final String key) {
        if (tryLock(key)) {
            try {
                return Either.left(supplier.get());
            } finally {
                unlock(key);
            }
        }
        return Either.right(new FailedToLock(ref + "/" + key));
    }

    public <T> T write(final Supplier<T> supplier) {
        refLock.writeLock().lock();
        try {
            return supplier.get();
        } finally {
            refLock.writeLock().unlock();
        }
    }

    public void write(final Runnable runnable) {
        refLock.writeLock().lock();
        try {
            runnable.run();
        } finally {
            refLock.writeLock().unlock();
        }
    }

    public <T> T read(final Supplier<T> supplier) {
        refLock.readLock().lock();
        try {
            return supplier.get();
        } finally {
            refLock.readLock().unlock();
        }
    }

    private <T, V extends Exception> T readThrow(final ThrowingSupplier<T, V> supplier) throws V {
        refLock.readLock().lock();
        try {
            return supplier.get();
        } finally {
            refLock.readLock().unlock();
        }
    }

    private boolean tryLock(final String key) {
        if (activeKeys.putIfAbsent(key, Thread.currentThread()) == null || activeKeys.get(key) == Thread.currentThread()) {
            refLock.writeLock().lock();
            return true;
        }
        return false;
    }

    private void unlock(final String key) {
        activeKeys.remove(key);
        refLock.writeLock().unlock();
    }

    public boolean reloadAll(final Runnable runnable) {
        if (refLock.isWriteLockedByCurrentThread()) {
            runnable.run();
            return true;
        } else {
            return false;
        }
    }

    public <T> Either<T, FailedToLock> lockWriteAll(final Supplier<T> supplier) {
        if (refLock.writeLock().tryLock()) {
            try {
                return Either.left(supplier.get());
            } finally {
                refLock.writeLock().unlock();
            }
        }
        return Either.right(new FailedToLock(ref));
    }

    private Set<String> getFiles() {
        return refCache.entrySet().stream().filter(e -> {
            final Either<Optional<StoreInfo>, Pair<String, UserData>> value = e.getValue();
            return (value.isLeft() && value.getLeft().isPresent());
        }).map(Entry::getKey).collect(Collectors.toSet());
    }

    public boolean isEmpty() {
        return refCache.values().stream().noneMatch(e -> e.fold(Optional<StoreInfo>::isPresent, u -> true));
    }

    public void refreshMetaData(final MetaData metaData, final String key, final String oldMetaDataVersion, final String newMetaDataVersion) {
        write(() -> {
            final Optional<StoreInfo> storeInfo = refCache.get(key).getLeft();
            storeInfo.ifPresent(si -> {
                if (oldMetaDataVersion.equals(si.getMetaDataVersion())) {
                    if (si.isMasterMetaData()) {
                        refCache.clear();
                        putKey(key, Optional.of(new StoreInfo(metaData, newMetaDataVersion)));
                    } else {
                        putKey(key, Optional.of(new StoreInfo(si.getStreamProvider(), metaData, si.getVersion(), newMetaDataVersion)));
                    }
                }
            });
        });
    }

    private Map<String, Either<Optional<StoreInfo>, Pair<String, UserData>>> refreshFiles(final Set<String> files) {
        final List<Either<Optional<Pair<String, StoreInfo>>, Exception>> refreshedRef = refreshRef(files);
        final List<Exception> faults = refreshedRef.stream()
                .filter(Either::isRight)
                .map(Either::getRight)
                .collect(Collectors.toList());
        if (!faults.isEmpty()) {
            throw new LinkedException(faults);
        }
        return refreshedRef.stream()
                .filter(Either::isLeft)
                .map(Either::getLeft)
                .flatMap(Optional::stream)
                .filter(p -> p.getRight() != null)
                .collect(Collectors.toMap(Pair::getLeft, p -> Either.left(Optional.of(p.getRight())), (a, b) -> {
                    throw new ShouldNeverHappenException("duplicate values for key");
                }, () -> getStorage(files.size())));
    }

    private List<Either<Optional<Pair<String, StoreInfo>>, Exception>> refreshRef(final Set<String> files) {
        return files.stream().map(key -> {
            try {
                return Either.<Optional<Pair<String, StoreInfo>>, Exception>left(Optional.of(Pair.of(key, load(key))));
            } catch (final RefNotFoundException ignore) {
                // Ignoring that ref wasn't found
            } catch (final Exception e) {
                return Either.<Optional<Pair<String, StoreInfo>>, Exception>right(new Exception(key + " in " + ref + " had the following error", e));
            }
            return Either.<Optional<Pair<String, StoreInfo>>, Exception>left(Optional.<Pair<String, StoreInfo>>empty());
        }).collect(Collectors.toCollection(() -> new ArrayList<>(files.size())));
    }

    Optional<StoreInfo> loadAndStore(final String key) {
        Optional<StoreInfo> storeInfoContainer = getKey(key);
        if (storeInfoContainer == null) {
            try {
                final StoreInfo storeInfo = load(key);
                storeInfoContainer = store(key, storeInfo);
            } catch (RefNotFoundException e) {
                throw new LoadException(e);
            }
        }
        return storeInfoContainer;
    }

    private StoreInfo load(final String key) throws RefNotFoundException {
        final SourceInfo sourceInfo = readThrow(() -> source.getSourceInfo(key, ref));
        if (sourceInfo != null) {
            try {
                final MetaData metaData = sourceInfo.readMetaData();
                if (!metaData.isHidden()) {
                    if (!sourceInfo.isMetaDataSource()) {
                        return new StoreInfo(sourceInfo.getSourceProvider(), metaData, sourceInfo.getSourceVersion(), sourceInfo.getMetaDataVersion());
                    } else {
                        return new StoreInfo(metaData, sourceInfo.getMetaDataVersion());
                    }
                }
                return null;
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return null;
    }

    private Optional<StoreInfo> store(final String key, final StoreInfo storeInfo) {
        Optional<StoreInfo> storeInfoContainer = isStorable(key, storeInfo);
        final Either<Optional<StoreInfo>, Pair<String, UserData>> computed = write(() -> refCache.compute(key, (k, v) -> {
            if (v == null) {
                return Either.left(storeInfoContainer);
            }
            return v;
        }));
        return computed != null && computed.isLeft() ? computed.getLeft() : storeInfoContainer;
    }

    private Optional<StoreInfo> isStorable(final String key, final StoreInfo storeInfo) {
        if (storeInfo != null && (keyRequestedIsMasterMeta(key, storeInfo) || keyRequestedIsNormalKey(key, storeInfo))) {
            return Optional.of(storeInfo);
        } else {
            return Optional.empty();
        }
    }

    private boolean keyRequestedIsNormalKey(final String key, final StoreInfo storeInfo) {
        return !key.endsWith("/") && storeInfo.isNormalKey();
    }

    private boolean keyRequestedIsMasterMeta(final String key, final StoreInfo storeInfo) {
        return key.endsWith("/") && storeInfo.isMasterMetaData();
    }

    public boolean refresh() {
        LOG.info("Reloading {}", ref);
        final Set<String> files = getFiles();
        final Map<String, Either<Optional<StoreInfo>, Pair<String, UserData>>> newMap = refreshFiles(files);
        boolean isRefreshed = !newMap.isEmpty();
        if (isRefreshed) {
            refCache = newMap;
        }
        return isRefreshed;
    }

    /*
     * This has to be checked when a user modifies a .metadata file for a directory
     */
    void checkIfPlainKeyExist(final String key) {
        if (key.endsWith("/")) {
            final String plainKey = key.substring(0, key.length() - 1);
            Either<Optional<StoreInfo>, Pair<String, UserData>> compute = read(() -> refCache.get(plainKey));
            if (compute == null) {
                compute = write(() -> refCache.compute(plainKey, (k, v) -> {
                    if (v == null) {
                        try {
                            return Either.left(Optional.ofNullable(load(k)));
                        } catch (RefNotFoundException fnfe) {
                            return null;
                        }
                    }
                    return v;
                }));
            }

            if (compute != null && compute.getLeft().isPresent()) {
                throw new WrappingAPIException(new KeyAlreadyExist(key, ref));
            }
        }
    }

    public Either<String, FailedToLock> modifyKey(final String key, final String finalRef, final byte[] data, final String oldVersion,
            final CommitMetaData commitMetaData) {
        return lockWrite(() -> {
            final Optional<StoreInfo> keyHolder = getKey(key);
            if (storageIsForbidden(keyHolder)) {
                throw new WrappingAPIException(new UnsupportedOperationException(key));
            }
            StoreInfo storeInfo = keyHolder.get();
            if (!oldVersion.equals(storeInfo.getVersion())) {
                throw new WrappingAPIException(new VersionIsNotSame());
            }
            final Pair<String, ThrowingSupplier<ObjectLoader, IOException>> newVersion = source.modifyKey(key, finalRef, data, oldVersion, commitMetaData);
            refCache.put(key, Either.left(Optional.of(new StoreInfo(getObjectStreamProvider(data, newVersion.getRight()), storeInfo.getMetaData(),
                    newVersion.getLeft(), storeInfo.getMetaDataVersion()))));
            return newVersion.getLeft();
        }, key);
    }

    public void deleteKey(final String key, final String finalRef, final CommitMetaData commitMetaData) {
        lockWrite(() -> {
            source.deleteKey(key, finalRef, commitMetaData);
            putKey(key, Optional.empty());
            return null;
        }, key);
    }

    public Either<String, FailedToLock> modifyMetadata(final MetaData metaData, final String oldMetaDataVersion, final CommitMetaData commitMetaData,
            final String key, final String finalRef) {
        return lockWrite(() -> {
            checkIfPlainKeyExist(key);
            final Optional<StoreInfo> storeInfo = getKey(key);
            if (storageIsForbidden(storeInfo)) {
                throw new WrappingAPIException(new UnsupportedOperationException(key));
            }
            final String newMetaDataVersion = source.modifyMetadata(metaData, oldMetaDataVersion, key, finalRef, commitMetaData);
            refreshMetaData(metaData, key, oldMetaDataVersion, newMetaDataVersion);
            return newMetaDataVersion;
        }, key);
    }

    private boolean storageIsForbidden(final Optional<StoreInfo> storeInfo) {
        return storeInfo == null || !storeInfo.isPresent() || storeInfo.get().getMetaData().isProtected();
    }

    String addKey(final String key, final String finalRef, final byte[] data, final MetaData metaData, final CommitMetaData commitMetaData) {
        final Pair<Pair<ThrowingSupplier<ObjectLoader, IOException>, String>, String> version = source.addKey(key, finalRef, data, metaData, commitMetaData);
        final Pair<ThrowingSupplier<ObjectLoader, IOException>, String> fileInfo = version.getLeft();
        storeIfNotHidden(key, this, new StoreInfo(getObjectStreamProvider(data, fileInfo.getLeft()), metaData, fileInfo.getRight(), version.getRight()));
        return fileInfo.getRight();
    }

    private ObjectStreamProvider getObjectStreamProvider(final byte[] data, final ThrowingSupplier<ObjectLoader, IOException> objectLoaderFactory) {
        final int size = data.length;
        if (size < threshold) {
            return new SmallObjectStreamProvider(data);
        } else {
            return new LargeObjectStreamProvider(() -> {
                return objectLoaderFactory.get().openStream();
            }, size);
        }
    }

    private void storeIfNotHidden(final String key, final RefHolder refStore, final StoreInfo newStoreInfo) {
        if (newStoreInfo.getMetaData().isHidden()) {
            refStore.putKey(key, Optional.empty());
        } else {
            refStore.putKey(key, Optional.of(newStoreInfo));
        }
    }

    public Pair<String, UserData> getUser(final String userKeyPath) {
        final String key = JitStaticConstants.USERS + userKeyPath;
        Either<Optional<StoreInfo>, Pair<String, UserData>> computed = read(() -> refCache.get(key));
        if (computed == null) {
            computed = write(() -> refCache.compute(key, this::mapKey));
        }
        if (computed != null) {
            return computed.getRight();
        }
        return null;
    }

    private Either<Optional<StoreInfo>, Pair<String, UserData>> mapKey(final String key, final Either<Optional<StoreInfo>, Pair<String, UserData>> value) {
        if (value == null || !value.isRight()) {
            Pair<String, UserData> user;
            try {
                user = source.getUser(key, this.ref);
            } catch (Exception e) {
                throw new WrappingAPIException(e);
            }
            if (user != null && user.isPresent()) {
                return Either.right(user);
            }
            return Either.right(Pair.ofNothing());
        }
        return value;
    }

    public Either<String, FailedToLock> updateUser(final String userKeyPath, final String username, final UserData data, final String version) {
        final String key = JitStaticConstants.USERS + Objects.requireNonNull(userKeyPath);
        Objects.requireNonNull(data);
        Objects.requireNonNull(version);
        Objects.requireNonNull(username);
        return lockWrite(() -> {
            final Either<Optional<StoreInfo>, Pair<String, UserData>> keyDataHolder = refCache.get(key);
            if (keyDataHolder == null || keyDataHolder.isLeft()) {
                throw new WrappingAPIException(new UnsupportedOperationException(key));
            }

            final Pair<String, UserData> userKeyData = keyDataHolder.getRight();
            if (!version.equals(userKeyData.getLeft())) {
                throw new WrappingAPIException(new VersionIsNotSame());
            }
            try {
                final String newVersion = source.updateUser(key, ref, username, data);
                refCache.put(key, Either.right(Pair.of(newVersion, data)));
                return newVersion;
            } catch (RefNotFoundException e) {
                throw new WrappingAPIException(new UnsupportedOperationException(key));
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to update " + key, e);
            }
        }, key);
    }

    public Either<String, FailedToLock> addUser(final String userKeyPath, final String username, final UserData data) {
        final String key = JitStaticConstants.USERS + Objects.requireNonNull(userKeyPath);
        return lockWrite(() -> {
            final Either<Optional<StoreInfo>, Pair<String, UserData>> keyDataHolder = refCache.get(key);
            if (keyDataHolder != null && keyDataHolder.isRight() && keyDataHolder.getRight().isPresent()) {
                throw new WrappingAPIException(new KeyAlreadyExist(key, ref));
            }
            try {
                String newVersion = source.addUser(key, ref, username, data);
                refCache.put(key, Either.right(Pair.of(newVersion, data)));
                return newVersion;
            } catch (RefNotFoundException e) {
                throw new WrappingAPIException(new UnsupportedOperationException(key));
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to add " + key, e);
            }
        }, key);
    }

    public void deleteUser(final String userKeyPath, final String username) {
        final String key = JitStaticConstants.USERS + Objects.requireNonNull(userKeyPath);
        write(() -> {
            try {
                refCache.remove(key);
                source.deleteUser(key, ref, username);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to delete " + key, e);
            }
        });

    }

}
