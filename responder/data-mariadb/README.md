## Overview

This jar contains JPA mapping customizations for MariaDB.
Because we support Derby and MariaDB we need to customize the mapping of the identity field which
have different generation strategy. For MariaDB we use auto increment strategy.
 
The persistence.xml file in the project responder-data references this package org.encryptedquery.responder.data.orm containing file orm-overrides.xml

Do not rename.
