import os
import json
import collections
import base64
import time
import boto3
from simple_salesforce import Salesforce

firehose_client = boto3.client('firehose')
ssm = boto3.client('ssm')

sfdc_sandbox = ssm.get_parameter(Name='/sfdc/sandbox/active', WithDecryption=False)
sfdc_domain = None

if sfdc_sandbox['Parameter']['Value'] == "0":
   sfdc_username = ssm.get_parameter(Name='/sfdc/username', WithDecryption=True)
   sfdc_password = ssm.get_parameter(Name='/sfdc/password', WithDecryption=True)
   sfdc_security_token = ssm.get_parameter(Name='/sfdc/security_token', WithDecryption=True)   
else:
   sfdc_username = ssm.get_parameter(Name='/sfdc/sandbox/username', WithDecryption=True)
   sfdc_password = ssm.get_parameter(Name='/sfdc/sandbox/password', WithDecryption=True)
   sfdc_security_token = ssm.get_parameter(Name='/sfdc/sandbox/security_token', WithDecryption=True)
   sfdc_domain = ssm.get_parameter(Name='/sfdc/sandbox/domain', WithDecryption=False)
   sfdc_domain = sfdc_domain['Parameter']['Value']
   

if sfdc_domain is None:
   sf = Salesforce(
      username=sfdc_username['Parameter']['Value'],
      password=sfdc_password['Parameter']['Value'],
      security_token=sfdc_security_token['Parameter']['Value']
   )
else:
   sf = Salesforce(
      username=sfdc_username['Parameter']['Value'],
      password=sfdc_password['Parameter']['Value'],
      security_token=sfdc_security_token['Parameter']['Value'],
      domain=sfdc_domain
   )


batch_size = 2000 # total records per BULK API call
interval = 1 # in seconds
total_batches = 1 # total number of BULK API Calls
start_value=1

def bulk_create(object_type):
      if object_type == 'Contact':
         # sf.bulk.Contact.insert(data)
         accountId = '0012w000005FSza' # HARD-CODED

         print(sfdc_username['Parameter']['Value'])
         
         
         for batch_counter in range(0, total_batches):
            dataset = []
            for object_counter in range(0, batch_size):
               dataset.append(
                  {
                     'FirstName': 'Test',
                     "LastName": "Test3%s%s%s" % (start_value, batch_counter, object_counter),
                     'AccountId': accountId,
                     'Email': "yaoys-%s%s%s@amazon.com" % (start_value, batch_counter, object_counter)
                  }
               )
            print(dataset)
            try:
               sf.bulk.Contact.insert(dataset)
               time.sleep(interval)
            except e as Exception:
               print(e)
               break


            
def bulk_delete():
   print('bulk delete')


# delivery_stream_name = os.environ['TARGET_DELIVERY_STREAM']


# def process_event(event):
#    # process each single payload from SFDC CDC
#    for record in event['Records']:
#       payload = record['kinesis']['data']
#       base64_bytes = payload.encode('ascii')
#       message_bytes = base64.b64decode(base64_bytes)
#       payload_data = message_bytes.decode('ascii')
#       process_sfdc_payload(json.loads(payload_data))
      
# def process_sfdc_payload(payload_data):
#    payload = payload_data['payload']
#    changeEventHeader = payload['ChangeEventHeader']
#    entityName = changeEventHeader['entityName']
#    changeType = changeEventHeader['changeType']
#    recordIds = changeEventHeader['recordIds']
   
#    # process each record Id from ONE SFDC CDC
#    objects = {}
#    for record_id in recordIds:     
#       str_record_id = "'%s'" % record_id
#       if entityName not in objects:
#          objects[entityName] = []
      
#       if objects[entityName].count(str_record_id) <= 0:
#          objects[entityName].append(str_record_id) 
   
   
#    for index in range(0, len(objects[entityName]), batch_size):
#       sfdc_query = 'SELECT %s FROM %s WHERE Id IN (%s)' % (','.join(getObjectFields(entityName)), entityName, ','.join(objects[entityName][index:index+batch_size]))
      
#       sf_data = sf.query_all(sfdc_query)
      
#       for sfdc_record in sf_data['records'] :
#          sfdc_record['UIND'] = changeType
#          delivery_data = json.dumps(sfdc_record).encode()
#          firehose_client.put_record(
#             DeliveryStreamName=delivery_stream_name,
#             Record={
#                 'Data': delivery_data
#             }
#          )
#          # Log to CloudWatch for debug
#          print(json.dumps(sfdc_record))
#       if interval > 0:
#          time.sleep(interval)

# def getObjectFields(obj):
#     fields = getattr(sf,obj).describe()['fields']
#     flist = [i['name'] for i in fields]
#     return flist


# def lambda_handler(event, context):
#    process_event(event)

if __name__== "__main__":
  bulk_create('Contact')