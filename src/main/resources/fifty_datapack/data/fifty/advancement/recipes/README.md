# Recipe advancements

Place recipe-unlock advancements in this directory as `.json` files.

The advancement ID follows its path. For example,
`recipes/craftable_core.json` is registered as
`fifty:recipes/craftable_core`.

Copy `recipe_unlock.json.example`, rename it to `<recipe-id>.json`, replace
the recipe ID and replace the safe `minecraft:impossible` placeholder with
an appropriate trigger. The recipe reward ID must match the value returned
by the corresponding `CustomRecipe#getRecipeId()`.
