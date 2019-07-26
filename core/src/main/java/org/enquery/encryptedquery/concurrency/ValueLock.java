package org.enquery.encryptedquery.concurrency;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.lang3.Validate;

/**
 * Mutually exclusive, reentrant lock on a value (as opposed to an instance). This class allows to
 * lock on the value. For example, if you want to synchronize on a string or integer value, using
 * the standard synchronized java block:<br/>
 * <br/>
 * 
 * <code>String name;<br/>
 *  synchronized (name) {}</code><br/>
 * <br/>
 * 
 * will not work because there may be multiple string instances with the same value. Same will
 * happen with integer values.<br/>
 * <br/>
 * 
 * Always lock/unlock using a try-finally construction.<br/>
 * <br/>
 * 
 * As for maps, this class depends on type <code>T</code> to provide consistent <code>equals</code>
 * and <code>hashCode</code> method implementations.
 * 
 * @param <T>
 */
public class ValueLock<T> {
	private static class LockInfo {
		Condition condition;
		Thread ownerThread;
		int refCount;

		public LockInfo(Condition condition) {
			this.condition = condition;
			this.ownerThread = Thread.currentThread();
			this.refCount = 1;
		}
	}

	private ReentrantLock lock = new ReentrantLock();
	private Map<T, LockInfo> locks = new HashMap<>();

	public void lock(T t) throws InterruptedException {
		lock.lock();
		try {
			while (locks.containsKey(t)) {
				LockInfo info = locks.get(t);
				if (info.ownerThread == Thread.currentThread()) {
					// we own the lock, increment reference count and return
					info.refCount++;
					return;
				} else {
					// lock is owned by another thread, wait
					// upon entering await, the lock is released
					info.condition.await();
					// upon returning from await, the lock is reacquired.
				}
			}
			locks.put(t, new LockInfo(lock.newCondition()));
		} finally {
			lock.unlock();
		}
	}

	public void unlock(T t) {
		lock.lock();
		try {
			LockInfo info = locks.get(t);
			Validate.notNull(info);
			info.refCount--;
			if (info.refCount == 0) {
				locks.remove(t);
				info.condition.signal();
			}
		} finally {
			lock.unlock();
		}
	}
}
