#!/bin/bash
# Setup Variables
MAVEN_URL=http://apache.claz.org/maven/maven-3/3.6.2/binaries/apache-maven-3.6.2-bin.tar.gz
MAVEN_VERSION=3.6.2
ENQUERY_REPO=https://github.com/En-Query/encryptedquery.git
ENQUERY_VERSION=2.2.2
# Handle yum installations
yum update -y
yum install -y git m4 java-1.8.0-openjdk-devel wget ant bzip2 centos-release-scl
yum-config-manager --enable rhel-server-rhscl-7-rpms
yum install -y devtoolset-7
echo -e "source /opt/rh/devtoolset-7/enable" > /etc/profile.d/devtoolset-7.sh
rm -f /opt/rh/devtoolset-7/root/bin/sudo
# Create the necessary folders
mkdir -p /opt/enquery/
mkdir -p /opt/enquery/jobs/
mkdir -p /opt/enquery/jobs/standalone/
mkdir -p /opt/enquery/jobs/flink/
mkdir -p /opt/enquery/jobs/hadoop/
mkdir -p /opt/enquery/app-libs/
mkdir -p /opt/enquery/results/
mkdir -p /opt/enquery/dataschemas/inbox
mkdir -p /opt/enquery/blob-s
chown -R centos /opt/enquery
# Switch to centos user for remaining setup
cd /home/centos
wget $MAVEN_URL
tar xf apache-maven-$MAVEN_VERSION-bin.tar.gz
echo -e "JAVA_HOME=$(readlink -f /usr/bin/java)\nPATH=$PATH:$JAVA_HOME:/home/centos/apache-maven-$MAVEN_VERSION/bin" >> /home/centos/.bashrc
source /home/centos/.bashrc
wget https://cmake.org/files/v3.12/cmake-3.12.3.tar.gz
tar zxvf cmake-3.*
cd cmake-3.*
./bootstrap --prefix=/usr/local
make -j$(nproc)
make install
git clone $ENQUERY_REPO
cd EncryptedQuery/parent
mvn clean install -P native-libs -D skip.gpu.native.libs=true -DskipTests
# Copy from the build location the responder tar file, untar it, then create a soft link. (Assuming you built the application off the home folder)
cp /home/centos/EncryptedQuery/responder/dist/target/encryptedquery-responder-dist-derbydb-*.tar.gz /opt/enquery/.
cd /opt/enquery/
tar -xf encryptedquery-responder-dist-derbydb-*.tar.gz
rm encryptedquery-responder-dist-derbydb-*.tar.gz
ln -s /opt/enquery/encryptedquery-responder-dist-* responder
# Copy over the Application library files
cp /home/centos/EncryptedQuery/standalone/app/target/encryptedquery-standalone-app-*.jar /opt/enquery/app-libs/.
cp /home/centos/EncryptedQuery/flink/jdbc/target/encryptedquery-flink-jdbc-*.jar /opt/enquery/app-libs/.
cp /home/centos/EncryptedQuery/flink/kafka/target/encryptedquery-flink-kafka-*.jar /opt/enquery/app-libs/.
cp /home/centos/EncryptedQuery/hadoop/mapreduce/target/encryptedquery-hadoop-mapreduce-*.jar /opt/enquery/app-libs/.

echo "query.execution.results.path=/opt/enquery/results" > /opt/enquery/responder/etc/encrypted.query.responder.business.cfg
echo "inbox.dir=/opt/enquery/dataschemas/inbox" > /opt/enquery/responder/etc/encrypted.query.responder.integration.cfg
echo -e "paillier.prime.certainty=128\npaillier.encrypt.query.task.count=2\npaillier.modulusBitSize=3072\n# paillier.encrypt.query.method.  Oneof ( Default, Fast, FastWithJNI )  Recommended FastWithJNI\npaillier.encrypt.query.method = FastWithJNI\n# paillier.column.processor is one of ( Basic, DeRooij, DeRooijJNI, Yao, YaoJNI, GPU ) Recommended DeRooijJNI\npaillier.column.processor=DeRooijJNI\n# Use only one of the below mod pow classes.  GMP has shown to be the faster.\n#paillier.mod.pow.class.name=org.enquery.encryptedquery.encryption.impl.ModPowAbstractionJavaImpl\npaillier.mod.pow.class.name=org.enquery.encryptedquery.encryption.impl.ModPowAbstractionGMPImpl" > /opt/enquery/responder/etc/org.enquery.encryptedquery.encryption.paillier.PaillierCryptoScheme.cfg
echo "blob.storage.root.url=file:///opt/enquery/blob-storage/" > /opt/enquery/responder/etc/encrypted.query.responder.data.cfg

chown -R centos /opt/enquery
