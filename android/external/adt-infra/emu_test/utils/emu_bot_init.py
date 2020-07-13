import os
import platform
import argparse
import shutil

parser = argparse.ArgumentParser(description='Download and unzip a list of files separated by comma')
parser.add_argument('--build-dir', dest='build_dir', action='store',
                    help='full path to build directory')

args = parser.parse_args()

def clean_up():
  """clean up build directory and qemu-gles-[pid] files"""

  # remove qemu-gles-[pid] files
  host = platform.system()
  if host in ["Linux", "Darwin"]:
    tmp_dir = "/tmp/android-%s" % os.environ["USER"]
    for f in os.listdir(tmp_dir):
      if f.startswith('qemu-gles-'):
        file_path = os.path.join(tmp_dir, f)
        print "Delete file %s" % file_path
        try:
          os.remove(file_path)
        except Exception as e:
          print "Error in deleting qemu-gles-[pid] %r" % e

  # remove build directory
  for f in os.listdir(args.build_dir):
    file_path = os.path.join(args.build_dir,f)
    try:
      if os.path.isfile(file_path):
        print "Delete file %s" % file_path
        os.remove(file_path)
      elif os.path.isdir(file_path):
        print "Delete directory %s" % file_path
        shutil.rmtree(file_path)
    except Exception as e:
      print "Error in deleting build directory %r" % e

if __name__ == "__main__":
  exit(clean_up())
