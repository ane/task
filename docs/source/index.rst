====================
 task documentation
====================

**task** is a Clojure library for concurrent computation. It provides simple and functional
concurrency primitives for doing asynchronous and concurrent computation.

.. code-block:: clojure

   [com.github.ane/task "0.1.0"] 

Key features
============

* **Value-oriented**. Tasks are just values that compute eventually. No callbacks required, regular
  ``deref`` or ``@`` is all you need.
* **Functional**. Tasks are composable and come with a set of operations that let you operate on
  them in a functional manner.
* **Interoperable**. Tasks are convertible to Clojure ``futures`` and ``promises`` -- and vice versa!
* **Asynchronous**. Tasks execute asynchronously using a Java `ForkJoinPool
  <https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ForkJoinPool.html?is-external=true>`_
  executor, and leverage the advanced `Java 8 Concurrency API
  <https://docs.oracle.com/javase/8/docs/technotes/guides/concurrency/changes8.html>`_ for concurrent computation.
* **Customizable**. You can customize your concurrency model by supplying your own `ExecutorService <https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ExecutorService.html>`_.

============
 User Guide
============

.. toctree::
   :maxdepth: 2

   overview
   guide

