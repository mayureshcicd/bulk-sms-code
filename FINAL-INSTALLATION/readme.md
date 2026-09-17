chmod +x install-docker.sh start.sh  
 
./start.sh
http://localhost:8081/login

http://localhost:8080/bulk-sms/index.html


docker compose -f setup.yml down --rmi all --volumes


/mnt/work/WHATS-APP-BULK-MESSAGE/NEW-CODE/FINAL-INSTALLATION/data/
├── sms/              # BulkMessageComposer application data
├── messages/         # MessageApprovalSystem application data
└── uploads/          # Uploaded/incoming media files

OpenWA and PostgreSQL use Docker-managed volumes because NTFS/FUSE does not
support the Unix file permissions required by those services.
