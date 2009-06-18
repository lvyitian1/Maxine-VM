/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
#include "mutex.h"
#include "log.h"
#include "threads.h"

void mutex_initialize(Mutex mutex) {
#if log_MONITORS
    log_println("mutex_initialize(%p, %p)", thread_self(), mutex);
#endif
#   if os_SOLARIS
	    if (mutex_init(mutex, LOCK_RECURSIVE | LOCK_ERRORCHECK, NULL) != 0) {
	        c_ASSERT(false);
	    }
#   elif os_LINUX || os_DARWIN
	    pthread_mutexattr_t mutex_attribute;
	    if (pthread_mutexattr_init(&mutex_attribute) != 0) {
	        c_ASSERT(false);
	    }
	    if (pthread_mutexattr_settype(&mutex_attribute, PTHREAD_MUTEX_RECURSIVE) != 0) {
	        c_ASSERT(false);
	    }
	    if (pthread_mutex_init(mutex, &mutex_attribute) != 0) {
	        c_ASSERT(false);
	    }
	    if (pthread_mutexattr_destroy(&mutex_attribute) != 0) {
	        c_ASSERT(false);
	    }
#   elif os_GUESTVMXEN
	    *mutex = guestvmXen_monitor_create();
#   else
        c_UNIMPLEMENTED();
#   endif
}

#if os_SOLARIS

    int mutex_enter(Mutex mutex) {
#       if log_MONITORS
            log_println("mutex_enter     (%p, %p)", thread_self(), mutex);
#       endif
        return mutex_lock(mutex);
    }

    int mutex_exit(Mutex mutex) {
#       if log_MONITORS
            log_println("mutex_exit     (%p, %p)", thread_self(), mutex);
#       endif
        return mutex_unlock(mutex);
    }

    void mutex_dispose(Mutex mutex) {
#       if log_MONITORS
            log_println("mutex_dispose   (%p, %p)", thread_self(), mutex);
#       endif
        if (mutex_destroy(mutex) != 0) {
            c_ASSERT(false);
        }
    }

#elif os_LINUX || os_DARWIN

	int mutex_enter(Mutex mutex) {
#       if log_MONITORS
            log_println("mutex_enter     (%p, %p)", thread_self(), mutex);
#       endif
		return pthread_mutex_lock(mutex);
	}

	int mutex_exit(Mutex mutex) {
#       if log_MONITORS
            log_println("mutex_exit     (%p, %p)", thread_self(), mutex);
#       endif
		return pthread_mutex_unlock(mutex);
	}

	void mutex_dispose(Mutex mutex) {
#       if log_MONITORS
            log_println("mutex_dispose   (%p, %p)", thread_self(), mutex);
#       endif
	    if (pthread_mutex_destroy(mutex) != 0) {
	        c_ASSERT(false);
	    }
	}

#elif os_GUESTVMXEN

	int mutex_enter(Mutex mutex) {
#       if log_MONITORS
            log_println("mutex_dispose   (%p, %p)", thread_self(), mutex);
#       endif
		if (guestvmXen_monitor_enter(*mutex) != 0) {
			c_ASSERT(false);
		}
		return 0;
	}

	int mutex_exit(Mutex mutex) {
#       if log_MONITORS
            log_println("mutex_exit     (%p, %p)", thread_self(), mutex);
#       endif
		if (guestvmXen_monitor_exit(*mutex) != 0) {
			c_ASSERT(false);
		}
		return 0;
	}

	Boolean mutex_isHeld(Mutex mutex) {
#       if log_MONITORS
            log_println("mutex_dispose   (%p, %p)", thread_self(), mutex);
#       endif
        return guestvmXen_holds_monitor(*mutex);
	}

#endif
