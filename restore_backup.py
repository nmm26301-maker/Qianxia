#!/usr/bin/env python3
"""Restore exact Qianxia artifacts; optionally decrypt the original signing archive."""
import argparse, base64, hashlib, io, json, zipfile
from pathlib import Path

def sha(data): return hashlib.sha256(data).hexdigest()
def safe(root, name):
 path=(root/name).resolve()
 if not path.is_relative_to(root.resolve()): raise ValueError('Invalid backup path')
 return path
def extract(data, destination):
 with zipfile.ZipFile(io.BytesIO(data)) as z:
  for name in z.namelist(): safe(destination,name)
  z.extractall(destination)
def restore(base, output, keyfile=None):
 manifest=json.loads((base/'BACKUP-MANIFEST.json').read_text())
 output.mkdir(parents=True,exist_ok=True)
 for entry in manifest['files']:
  encoded=[]
  for part in entry['parts']:
   data=safe(base,part['path']).read_bytes()
   if sha(data)!=part['sha256']: raise ValueError('Corrupt backup part: '+part['path'])
   encoded.append(data)
  data=base64.b64decode(b''.join(encoded),validate=True)
  if len(data)!=entry['size_bytes'] or sha(data)!=entry['sha256']: raise ValueError('Artifact checksum mismatch')
  target=safe(output,entry['filename']); target.write_bytes(data)
  print('Verified:',entry['filename'])
  if entry['filename'].endswith('-source.zip'): extract(data,output/'source')
 if keyfile:
  from cryptography.hazmat.primitives.ciphers.aead import AESGCM
  keyinfo=json.loads(keyfile.read_text()); meta=manifest['signing_backup']
  blob=safe(base,meta['path']).read_bytes()
  if sha(blob)!=meta['sha256'] or sha(blob)!=keyinfo['backup_sha256']: raise ValueError('Signing backup checksum mismatch')
  enc=json.loads(blob)
  plain=AESGCM(base64.b64decode(keyinfo['key_base64'],validate=True)).decrypt(base64.b64decode(enc['nonce_base64'],validate=True),base64.b64decode(enc['ciphertext_base64'],validate=True),enc['format'].encode())
  if sha(plain)!=enc['plaintext_sha256'] or sha(plain)!=meta['original_zip_sha256']: raise ValueError('Signing archive mismatch')
  (output/'Qianxia-signing-backup.zip').write_bytes(plain)
  extract(plain,output/'original-signing')
  print('Verified and restored: original signing key and password instructions')
 return manifest

if __name__=='__main__':
 p=argparse.ArgumentParser(); p.add_argument('--output',type=Path,default=Path('restored')); p.add_argument('--recovery-key',type=Path)
 args=p.parse_args(); restore(Path(__file__).resolve().parent,args.output,args.recovery_key)
