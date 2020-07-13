from recipe_engine import recipe_test_api


class BisectTesterTestApi(recipe_test_api.RecipeTestApi):
  @recipe_test_api.mod_test_data
  @staticmethod
  def tempfile(tempfile):
    return tempfile

  def __call__(self, tempfile):
    return self.tempfile(tempfile)
