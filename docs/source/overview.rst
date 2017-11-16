Overview
========

Requirements
------------

* Java 8 or later
* Clojure 1.8 or later


Installation
------------

The library is available on `Clojars <https://clojars.org/>`_. Add this to your Leiningen/Boot file.

.. code-block:: clojure

   [com.github.ane/task "0.1.0"]

The default namespace is ``task.core``. It is advised you refer to the library explicitly.

.. code-block:: clojure

   (ns my-program.core
      (:require [task.core :as task]))

   (def eventual-value (task/run 123))
