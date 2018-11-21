## Overview

This jar contains JPA mapping customizations for Apache Derby.
Because we support Derby and MariaDB we need to customize the mapping of the identity field which
have different generation strategy. For Derby we use sequence generator strategy.
 
The persistence.xml file in the project querier-data references this package org.encryptedquery.querier.data.orm containing file orm-overrides.xml

Do not rename.
