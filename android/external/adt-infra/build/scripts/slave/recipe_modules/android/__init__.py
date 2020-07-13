DEPS = [
  'bot_update',
  'file',
  'gclient',
  'json',
  'path',
  'properties',
  'python',
  'repo',
  'step',
]

from recipe_engine.recipe_api import Property

PROPERTIES = {
  'patch_project': Property(default=None),
  'revision': Property(default='HEAD'),
  'clobber': Property(default=None, kind=bool),
}
