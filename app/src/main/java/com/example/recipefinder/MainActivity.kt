package com.example.recipefinder

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.recipefinder.ui.theme.RecipeFinderTheme
import coil.compose.AsyncImage
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.ImeAction
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import com.squareup.moshi.Json
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RecipeFinderTheme {
                Surface{
                    RecipeFinderApp()
                }
            }
        }
    }
}

class RecipeViewModel : ViewModel() {
    private val repository = RecipeRepository()

    var searchQuery by mutableStateOf(TextFieldValue(""))
    var recipes by mutableStateOf<List<RecipeItem>>(emptyList())
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var selectedRecipeId by mutableStateOf<Int?>(null)
    var recipeDetails by mutableStateOf<RecipeDetails?>(null)

    fun performSearch() {
        if (searchQuery.text.isNotEmpty()) {
            isLoading = true
            error = null
            viewModelScope.launch {
                repository.searchRecipes(searchQuery.text)
                    .catch { e ->
                        error = e.message ?: "Unknown error"
                        isLoading = false
                    }
                    .collect { fetchedRecipes ->
                        recipes = fetchedRecipes
                        isLoading = false
                    }
            }
        }
    }

    fun getRecipeDetails(id: Int) {
        isLoading = true
        viewModelScope.launch {
            try {
                val details = repository.getRecipeDetails(id)
                recipeDetails = details
                isLoading = false
            } catch (e: Exception) {
                error = e.message ?: "Error fetching recipe details"
                isLoading = false
            }
        }
    }

    fun getRecipeById(id: Int): RecipeDetails? {
        return recipeDetails
    }
}

@Composable
fun RecipeFinderApp(viewModel: RecipeViewModel = viewModel()) {
    val navController = rememberNavController()
    val configuration = LocalConfiguration.current

    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        RecipeListWithDetailsScreen(viewModel = viewModel)
    } else {
        NavHost(navController = navController, startDestination = "recipeList") {
            composable("recipeList") {
                RecipeListScreen(viewModel, onRecipeClick = { recipeId ->
                    viewModel.selectedRecipeId = recipeId
                    navController.navigate("recipeDetails/$recipeId")
                })
            }
            composable(
                "recipeDetails/{recipeId}",
                arguments = listOf(navArgument("recipeId") { type = NavType.IntType })
            ) { backStackEntry ->
                val recipeId = backStackEntry.arguments?.getInt("recipeId")
                if (recipeId != null) {
                    RecipeDetailsScreen(recipeId = recipeId, viewModel = viewModel, navController = navController)
                }
            }
        }
    }
}

@Composable
fun RecipeListScreen(
    viewModel: RecipeViewModel,
    onRecipeClick: (Int) -> Unit
) {
    var hasSearched by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchBar(
                query = viewModel.searchQuery,
                onQueryChange = { viewModel.searchQuery = it },
                onSearch = {
                    viewModel.performSearch()
                    hasSearched = true
                },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                viewModel.performSearch()
                hasSearched = true
            }) {
                Icon(Icons.Filled.Search, contentDescription = "Search")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when {
            viewModel.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            hasSearched && viewModel.recipes.isEmpty() -> Text("Recipe not found", color = Color.Red)
            viewModel.error != null -> Text("Error: ${viewModel.error}", color = MaterialTheme.colorScheme.error)
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(viewModel.recipes) { recipe ->
                        RecipeItemView(recipe = recipe, onClick = { onRecipeClick(recipe.id) })
                    }
                }
            }
        }
    }
}

@Composable
fun RecipeListWithDetailsScreen(viewModel: RecipeViewModel) {
    Row(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(0.4f).padding(8.dp)) {
            RecipeListScreen(viewModel, onRecipeClick = { recipeId ->
                viewModel.selectedRecipeId = recipeId
            })
        }
        Column(modifier = Modifier.weight(0.6f).padding(8.dp)) {
            viewModel.selectedRecipeId?.let { recipeId ->
                RecipeDetailsScreen(recipeId = recipeId, viewModel = viewModel, navController = null)
            } ?: run {
                Text("Select a recipe to view details", modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
fun SearchBar(
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        label = { Text("Search for recipes...") },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardOptions.Default.keyboardType,
            imeAction = ImeAction.Search
        ),
        keyboardActions = KeyboardActions(
            onSearch = {
                keyboardController?.hide()
                onSearch()
            }
        ),
        modifier = modifier,
        singleLine = true
    )
}

@Composable
fun RecipeItemView(recipe: RecipeItem, onClick: () -> Unit) {
    val imageUrl = recipe.image.replace("http://", "https://")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = "Recipe Image",
            modifier = Modifier.size(64.dp).padding(end = 8.dp)
        )
        Column {
            Text(text = recipe.title, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailsScreen(recipeId: Int, viewModel: RecipeViewModel, navController: NavController?) {
    val recipe = viewModel.getRecipeById(recipeId)

    if (recipe != null) {
        Scaffold(
            topBar = {
                if (navController != null) {
                    SmallTopAppBar(
                        title = { Text("Recipe Details") },
                        navigationIcon = {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    )
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                AsyncImage(
                    model = recipe.image,
                    contentDescription = "Recipe Image",
                    modifier = Modifier.size(200.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(text = "Ingredients:", style = MaterialTheme.typography.bodyLarge)
                recipe.ingredients.forEach { ingredient ->
                    Text(text = "- $ingredient")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(text = "Instructions:", style = MaterialTheme.typography.bodyLarge)
                recipe.instructions.forEach { instruction ->
                    Text(text = instruction)
                }
            }
        }
    } else {
        Text("Recipe not found", color = Color.Red, modifier = Modifier.padding(16.dp))
    }
}

interface SpoonacularApi {
    @GET("recipes/complexSearch")
    suspend fun searchRecipes(
        @Query("query") query: String,
        @Query("apiKey") apiKey: String = "97af6d953b414a27a6c8aa4b18fb66e4"
    ): RecipeSearchResponse

    @GET("recipes/{id}/ingredientWidget.json")
    suspend fun getRecipeIngredients(
        @Path("id") id: Int,
        @Query("apiKey") apiKey: String = "97af6d953b414a27a6c8aa4b18fb66e4"
    ): RecipeIngredientsResponse

    @GET("recipes/{id}/analyzedInstructions")
    suspend fun getRecipeInstructions(
        @Path("id") id: Int,
        @Query("apiKey") apiKey: String = "97af6d953b414a27a6c8aa4b18fb66e4"
    ): List<Instruction>
}

object RetrofitInstance {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val api: SpoonacularApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.spoonacular.com/")
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().add(KotlinJsonAdapterFactory()).build()))
            .client(client)
            .build()
            .create(SpoonacularApi::class.java)
    }
}

class RecipeRepository {
    private val api = RetrofitInstance.api

    fun searchRecipes(query: String) = flow {
        val response = api.searchRecipes(query)
        emit(response.results)
    }.catch { e ->
        throw e
    }

    suspend fun getRecipeDetails(id: Int): RecipeDetails {
        val ingredients = api.getRecipeIngredients(id)
        val instructions = api.getRecipeInstructions(id)
        return RecipeDetails(
            id = id,
            image = "https://spoonacular.com/recipeImages/$id-636x393.jpg",
            ingredients = ingredients.ingredients.map { it.originalString },
            instructions = instructions.flatMap { it.steps.map { step -> step.step } }
        )
    }
}

data class RecipeSearchResponse(
    @Json(name = "results") val results: List<RecipeItem>
)

data class RecipeItem(
    @Json(name = "id") val id: Int,
    @Json(name = "title") val title: String,
    @Json(name = "image") val image: String
)

data class RecipeDetails(
    val id: Int,
    val image: String,
    val ingredients: List<String>,
    val instructions: List<String>
)

data class RecipeIngredientsResponse(
    @Json(name = "ingredients") val ingredients: List<Ingredient>
)

data class Ingredient(
    @Json(name = "originalString") val originalString: String
)

data class Instruction(
    @Json(name = "steps") val steps: List<InstructionStep>
)

data class InstructionStep(
    @Json(name = "step") val step: String
)